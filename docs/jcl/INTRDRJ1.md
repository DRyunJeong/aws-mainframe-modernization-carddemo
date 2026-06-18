# INTRDRJ1 — 내부 리더(INTRDR)를 통한 연쇄 잡 제출기

- **유형**: JCL
- **한 줄 요약**: FTP 테스트 데이터셋을 백업한 뒤, PDS 멤버 `INTRDRJ2`를 내부 리더(INTRDR) 스트림에 직접 주입하여 두 번째 잡을 자동 제출하는 연쇄 실행 유틸리티

---

## 기능 설명

`INTRDRJ1`은 두 가지 역할을 순서대로 수행합니다.

1. **데이터셋 백업**: IDCAMS의 `REPRO` 명령으로 FTP 테스트 소스 파일(`AWS.M2.CARDEMO.FTP.TEST`)을 백업 파일(`AWS.M2.CARDEMO.FTP.TEST.BKUP`)으로 복사합니다.
2. **후속 잡 자동 제출**: IEBGENER를 사용하여 PDS 멤버(`INTRDRJ2`)를 읽어 내부 리더(`SYSOUT=(A,INTRDR)`)로 출력합니다. 내부 리더는 JES(Job Entry Subsystem)의 특수 출력 클래스로, 출력 레코드를 JCL로 해석하여 새로운 잡을 즉시 큐에 제출합니다.

이 패턴은 z/OS에서 "잡 체이닝(job chaining)"을 구현하는 전통적인 방법입니다. 현대 Java/CI 환경의 **Pipeline trigger** 또는 **workflow dispatch** 패턴에 직접 대응됩니다.

```
INTRDRJ1 실행
  └─ IDCAMS: FTP.TEST → FTP.TEST.BKUP 복사
  └─ IEBGENER: PDS(INTRDRJ2) → INTRDR 주입
                                └─ JES: INTRDRJ2 잡 자동 제출
```

---

## 스텝 구성

| 스텝명  | EXEC PGM/PROC | 역할 |
|---------|---------------|------|
| `IDCAMS` | `PGM=IDCAMS` | `REPRO` 명령으로 FTP 테스트 데이터셋을 백업 데이터셋에 복제 |
| `STEP01` | `PGM=IEBGENER` | PDS 멤버 `INTRDRJ2`를 내부 리더(INTRDR)에 전달하여 후속 잡 자동 제출 |

### 스텝별 상세

**IDCAMS 스텝** (라인 6–12)

```jcl
//IDCAMS   EXEC PGM=IDCAMS,DYNAMNBR=200
//IN  DD DSN=AWS.M2.CARDEMO.FTP.TEST,DISP=SHR
//OUT DD DSN=AWS.M2.CARDEMO.FTP.TEST.BKUP,DISP=SHR
//SYSIN DD *
  REPRO IFILE(IN) OFILE(OUT)
```

- `DYNAMNBR=200`: 동적 할당 가능한 DD 수 상한을 200으로 설정
- `REPRO IFILE(IN) OFILE(OUT)`: VSAM 또는 순차 파일을 레코드 단위로 복사하는 IDCAMS 유틸리티 명령. Java의 `Files.copy()` 에 해당

**STEP01 스텝** (라인 14–19)

```jcl
//STEP01  EXEC PGM=IEBGENER
//SYSUT1 DD DSN=AWS.M2.CARDDEMO.JCL(INTRDRJ2),DISP=SHR
//SYSUT2 DD SYSOUT=(A,INTRDR),DCB=(LRECL=80,BLKSIZE=80)
```

- `SYSUT1`: 입력 — PDS `AWS.M2.CARDDEMO.JCL`의 멤버 `INTRDRJ2` (80바이트 고정 길이 JCL 텍스트)
- `SYSUT2`: 출력 — `SYSOUT=(A,INTRDR)`. `A`는 출력 클래스, `INTRDR`은 내부 리더 라이터(writer) 지정. JES는 이 스트림을 JCL로 파싱하여 즉시 잡 제출 큐에 올림
- `DCB=(LRECL=80,BLKSIZE=80)`: JCL 표준인 80바이트 레코드 형식을 명시. IEBGENER는 `SYSIN DD DUMMY`이면 변환 없이 원본 레코드를 그대로 복사

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 — 인라인 스텝만 사용

- **호출 프로그램 (EXEC PGM)**:
  - `IDCAMS` — IBM 시스템 유틸리티: VSAM/순차 파일 관리 (REPRO, DEFINE, DELETE 등)
  - `IEBGENER` — IBM 데이터셋 복사 유틸리티: SYSUT1 → SYSUT2 레코드 단위 복사

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDEMO.FTP.TEST` — 입력: FTP 테스트용 소스 데이터셋 (읽기 전용, `DISP=SHR`)
  - `AWS.M2.CARDEMO.FTP.TEST.BKUP` — 출력: 위 파일의 백업 사본 (`DISP=SHR`, 사전 존재 필요)
  - `AWS.M2.CARDDEMO.JCL(INTRDRJ2)` — 입력: 후속 제출될 JCL이 담긴 PDS 멤버 (주의: HLQ가 `CARDEMO`로 위의 `CARDEMO`와 철자 동일하지만 상위 데이터셋명은 `.JCL`로 다름)

- **선행/후행 잡**:
  - 선행 잡: 명시적 선행 조건 없음. 단, `AWS.M2.CARDEMO.FTP.TEST` 데이터셋이 다른 잡에 의해 enqueue되어 있으면 `DISP=SHR` 충돌 발생 가능
  - 후행 잡: `INTRDRJ2` — STEP01 성공 시 JES에 의해 자동 제출됨 (잡 이름/내용은 `AWS.M2.CARDDEMO.JCL(INTRDRJ2)` 멤버 내용에 따라 결정)

---

## Java/현대화 노트

### 1. 내부 리더(INTRDR) 패턴 — Java 대응

메인프레임의 내부 리더는 JES 스풀에 JCL 텍스트를 직접 주입하는 메커니즘입니다. Java 현대화 시 다음과 같이 대응할 수 있습니다.

```java
// 메인프레임: IEBGENER → INTRDR → JES가 INTRDRJ2 잡 자동 실행
// Java 현대화 대응 예시 1 — AWS Step Functions 체이닝
StateMachine chain = StateMachine.Builder.create(this, "IntrdrChain")
    .definition(backupStep.next(triggerIntrdrJ2Step))
    .build();

// Java 현대화 대응 예시 2 — Jenkins Pipeline trigger
// stage('BackupAndTrigger') {
//     steps {
//         sh 'aws s3 cp s3://ftp-test s3://ftp-test-bkup --recursive'
//         build job: 'INTRDRJ2', wait: false
//     }
// }
```

### 2. IDCAMS REPRO — Java 대응

```java
// 메인프레임: REPRO IFILE(IN) OFILE(OUT)
// Java: 레코드 단위 복사 → Files.copy() 또는 스트림 파이프
Path source = Path.of("ftp-test.dat");
Path backup = Path.of("ftp-test-bkup.dat");
Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
```

VSAM KSDS 파일이라면 `REPRO`는 키 순서로 레코드를 복사합니다. Java에서는 VSAM 대신 DynamoDB/RDS를 사용하는 경우 테이블 복제 로직으로 대체됩니다.

### 3. 데이터셋 명칭 불일치 주의

라인 8의 `AWS.M2.CARDEMO.FTP.TEST`와 라인 17의 `AWS.M2.CARDDEMO.JCL(INTRDRJ2)`에서 HLQ 패턴이 `CARDEMO`(5자) vs `CARDDEMO`(6자)로 혼재합니다. 이는 이 코드베이스 전반에 걸쳐 알려진 불일치로, 실제 운영 환경에서는 카탈로그(`LISTCAT`)로 정확한 DSN을 반드시 확인해야 합니다.

### 4. IEBGENER + SYSIN DUMMY 패턴

`SYSIN DD DUMMY`는 변환 제어문 없이 입력을 그대로 출력으로 복사하라는 의미입니다. Java에서는 단순 `InputStream → OutputStream` 파이프입니다. 이 파일이 INTRDR로 흘러들어가는 것이 핵심 동작이므로, 현대화 시 "파일 복사" 자체보다 "트리거 전달" 의미를 보존하는 것이 중요합니다.

### 5. 잡 체이닝 현대화 권고

| 메인프레임 패턴 | 현대 대응 |
|----------------|-----------|
| INTRDR를 통한 잡 제출 | AWS Step Functions / Apache Airflow DAG / Jenkins Pipeline |
| JES 잡 큐 | SQS / Kubernetes Job Queue |
| `NOTIFY=&SYSUID` | SNS 알림 / Slack webhook |
| `CLASS=A` 잡 클래스 | 컨테이너 리소스 클래스(CPU/메모리 limit) |
