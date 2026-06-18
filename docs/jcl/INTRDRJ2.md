# INTRDRJ2 — IMS DemoDB/DemoDX VSAM 물리 파일 생성 잡

- **유형**: JCL
- **한 줄 요약**: IDCAMS REPRO를 사용하여 IMS DemoDB용 백업 데이터셋에서 내부 리더(INTRDR) 경유 제출 대상 데이터셋으로 데이터를 복사하는 잡으로, INTRDRJ1에 의해 내부 리더를 통해 동적으로 제출된다.

---

## 기능 설명

INTRDRJ2는 IMS 데모 데이터베이스(DEMODB)와 그 인덱스(DEMODX)에 사용할 VSAM 물리 파일을 생성하기 위한 목적으로 기술되어 있다.

실제 수행 내용은 IDCAMS 유틸리티의 REPRO 명령으로, 소스 데이터셋(`AWS.M2.CARDEMO.FTP.TEST.BKUP`)의 내용을 목적지 데이터셋(`AWS.M2.CARDEMO.FTP.TEST.BKUP.INTRDR`)으로 복사한다.

**핵심 특징: 내부 리더(INTRDR)에 의한 2단계 연쇄 제출**

이 잡은 단독 실행이 목적이 아니다. INTRDRJ1이 먼저 실행되며, INTRDRJ1의 STEP01에서 IEBGENER가 이 INTRDRJ2 JCL 멤버(`AWS.M2.CARDDEMO.JCL(INTRDRJ2)`) 자체를 `SYSOUT=(A,INTRDR)` — 즉 내부 입력 스트림(JES Internal Reader) — 으로 출력함으로써 INTRDRJ2를 JES 입력 큐에 동적으로 제출한다.

```
[사람 또는 스케줄러]
       |
       V
  INTRDRJ1 실행
  ├── STEP1 (IDCAMS REPRO): FTP 테스트 데이터 → 백업 데이터셋 복사
  └── STEP01 (IEBGENER):   INTRDRJ2 JCL 멤버 → INTRDR 제출
                                    |
                                    V (JES가 INTRDRJ2를 새 잡으로 자동 제출)
                               INTRDRJ2 실행
                               └── IDCAMS (IDCAMS REPRO): 백업 → BKUP.INTRDR 복사
```

Java 관점에서의 유사 패턴:

```java
// INTRDRJ1 역할: 부모 프로세스가 자식 프로세스를 동적으로 제출
ProcessBuilder pb = new ProcessBuilder("java", "-jar", "intrdrj2-job.jar");
pb.start(); // INTRDR에 JCL을 흘려보내는 것과 유사
```

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|--------------|------|
| IDCAMS | `PGM=IDCAMS` | REPRO 명령으로 `AWS.M2.CARDEMO.FTP.TEST.BKUP` 데이터셋의 내용을 `AWS.M2.CARDEMO.FTP.TEST.BKUP.INTRDR` 데이터셋으로 복사한다. DYNAMNBR=200으로 동적 할당 최대 200개 허용. |

**IDCAMS SYSIN 명령 상세:**

```
REPRO IFILE(IN) OFILE(OUT)
```

- `IFILE(IN)`: DD명 IN이 가리키는 데이터셋(`AWS.M2.CARDEMO.FTP.TEST.BKUP`)을 입력으로 사용
- `OFILE(OUT)`: DD명 OUT이 가리키는 데이터셋(`AWS.M2.CARDEMO.FTP.TEST.BKUP.INTRDR`)으로 출력

Java 유사 코드:

```java
// Files.copy()와 유사한 레코드 단위 복사
Files.copy(
    Path.of("AWS.M2.CARDEMO.FTP.TEST.BKUP"),
    Path.of("AWS.M2.CARDEMO.FTP.TEST.BKUP.INTRDR"),
    StandardCopyOption.REPLACE_EXISTING
);
```

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음. 외부 PROC 또는 INCLUDE 사용 없음.

- **호출 프로그램 (EXEC PGM)**:
  - `IDCAMS` — IBM Access Method Services. VSAM 및 비-VSAM 데이터셋 관리, 복사(REPRO), 삭제(DELETE), 정의(DEFINE) 등을 수행하는 IBM 제공 시스템 유틸리티. z/OS 표준 링크 라이브러리에 상주하며 별도 설치 불필요.

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDEMO.FTP.TEST.BKUP` (DD: IN, DISP=SHR) — INTRDRJ1의 첫 번째 스텝이 생성한 백업 데이터셋. REPRO의 입력 소스.
  - `AWS.M2.CARDEMO.FTP.TEST.BKUP.INTRDR` (DD: OUT, DISP=SHR) — REPRO의 출력 대상. 이름의 `.INTRDR` 접미사는 내부 리더 연관 데이터셋임을 관례적으로 표시하나 (추측), 실제로는 일반 순차/VSAM 데이터셋일 수 있음.

- **선행/후행 잡**:
  - **선행 잡**: `INTRDRJ1` — 이 잡을 내부 리더를 통해 제출하는 부모 잡. INTRDRJ1의 STEP01(IEBGENER)이 `AWS.M2.CARDDEMO.JCL(INTRDRJ2)` PDS 멤버를 `SYSOUT=(A,INTRDR)`로 출력함으로써 INTRDRJ2를 JES 큐에 제출한다 (INTRDRJ1 소스 17~18행 참조).
  - **후행 잡**: 명시된 후행 잡 없음. INTRDRJ2 완료 후 어떤 잡이 이어지는지는 이 소스에서 확인 불가. (추측) IMS DemoDB VSAM 파일 생성 완료 후 IMS 데이터베이스 정의/로드 잡이 뒤따를 수 있음.

---

## Java/현대화 노트

### 1. INTRDR(내부 리더) 패턴 — 동적 잡 제출

메인프레임의 내부 리더(Internal Reader)는 실행 중인 배치 잡이 또 다른 잡을 JES 입력 큐에 동적으로 제출하는 메커니즘이다. INTRDRJ1의 `SYSOUT=(A,INTRDR)` DD가 이 역할을 한다 (INTRDRJ1 18행).

Java/현대 환경에서의 대응 방식:

```java
// 방법 1: ProcessBuilder로 자식 프로세스 제출
ProcessBuilder pb = new ProcessBuilder("java", "-jar", "child-job.jar");
pb.inheritIO().start();

// 방법 2: Spring Batch JobLauncher로 후속 잡 기동
@Autowired JobLauncher jobLauncher;
@Autowired Job intrdrJ2Job;

jobLauncher.run(intrdrJ2Job, new JobParametersBuilder()
    .addString("inputDataset", "bkup-dataset-path")
    .toJobParameters());

// 방법 3: AWS Step Functions / Apache Airflow 태스크 체이닝
// INTRDRJ1 → INTRDRJ2 순서를 DAG 또는 State Machine으로 표현
```

INTRDR 패턴 현대화 시 주의점:
- 메인프레임에서 INTRDR 제출은 JES가 잡 스케줄링/우선순위/클래스를 완전히 제어한다. 현대화 시 이 제어 지점을 오케스트레이터(Airflow, Step Functions 등)로 이전해야 한다.
- `CLASS=A`, `MSGCLASS=H`, `REGION=5M` 같은 JCL 파라미터는 현대 환경에서 실행 큐/로그 레벨/메모리 설정으로 매핑된다.

### 2. IDCAMS REPRO — 범용 데이터셋 복사

IDCAMS REPRO는 VSAM 및 순차(PS) 데이터셋을 레코드 단위로 복사하는 메인프레임 표준 방식이다. Java에서는 `Files.copy()`, Apache Commons IO의 `FileUtils.copyFile()`, 또는 스트리밍 파이프(`InputStream` → `OutputStream`)로 대체한다.

EBCDIC 주의: 메인프레임 데이터셋은 EBCDIC로 인코딩되어 있다. Java로 현대화 시 EBCDIC → UTF-8 변환이 필요하며, VSAM 레코드의 고정 길이(LRECL)와 블록 크기(BLKSIZE)도 고려해야 한다.

```java
// EBCDIC → UTF-8 변환 예시
Charset ebcdic = Charset.forName("IBM037");
byte[] rawBytes = Files.readAllBytes(inputPath);
String converted = new String(rawBytes, ebcdic);
Files.writeString(outputPath, converted, StandardCharsets.UTF_8);
```

### 3. JCL JOB 카드 파라미터 매핑

| JCL 파라미터 | 값 | Java/현대 대응 |
|---|---|---|
| `CLASS=A` | 잡 실행 클래스(우선순위 큐) | 실행 큐 / 스레드 우선순위 |
| `MSGCLASS=H` | 잡 로그 출력 목적지 | 로그 출력 레벨/대상 (SLF4J 등) |
| `MSGLEVEL=(1,1)` | JCL/메시지 상세 수준 | 로그 verbosity 설정 |
| `REGION=5M` | 메모리 할당 5MB | JVM `-Xmx` 힙 설정 |
| `NOTIFY=&SYSUID` | 완료 시 제출자에게 알림 | 잡 완료 이벤트/알림 (SNS, 이메일 등) |

### 4. 잡 이름 규칙 (추측)

`INTRDRJ1` → `INTRDRJ2` 순번 접미사는 내부 리더 연쇄 잡의 실행 순서를 나타내는 CardDemo 프로젝트 내부 명명 관례로 보인다. 현대화 시 이 순서 의존성을 명시적인 워크플로 의존성(Airflow `>>` 연산자, Step Functions `Next` 등)으로 표현해야 한다.

### 5. IMS DemoDB와의 관계

JCL 주석에서 "IMS DEMODB / DEMODX VSAM 물리 파일 생성"이라고 명시되어 있다 (4~6행). `app/app-authorization-ims-db2-mq/` 옵션 모듈의 IMS HIDAM 데이터베이스(PAUTSUM0 루트, PAUTDTL1 자식) 초기화 흐름의 일부로 추정된다 (추측). 실제 VSAM DEFINE/REPRO 절차가 이 잡 이전에 선행되어야 한다.
