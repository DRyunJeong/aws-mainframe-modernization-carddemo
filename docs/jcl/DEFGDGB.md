# DEFGDGB — GDG 베이스 정의 잡

- **유형**: JCL
- **한 줄 요약**: CardDemo 프로젝트에서 야간 배치가 사용하는 6개 GDG(Generation Data Group) 베이스를 IDCAMS로 카탈로그에 등록한다. 최초 환경 구성 시 단 한 번 실행하는 선행 준비 잡이다.

---

## 기능 설명

GDG(Generation Data Group)는 메인프레임의 "버전 관리형 데이터셋" 개념이다. 동일한 논리 이름 아래 세대(generation)를 자동으로 관리하며, 가장 최신 세대를 `(0)`, 이전 세대를 `(-1)`, `(-2)` 와 같은 상대 인덱스로 참조한다. Java로 비유하면 **고정 용량 원형 큐(CircularDeque)를 파일 시스템에 구현한 것**이다.

이 잡은 실제 데이터셋을 만드는 것이 아니라, GDG **베이스(base)**만 카탈로그에 등록한다. 베이스는 "이 이름 아래 최대 N개의 세대를 관리하겠다"는 메타데이터 엔트리다. Java에서 `new LinkedList<>(capacity)` 선언 자체와 유사하며, 실제 파일(세대)은 이후 배치 잡이 실행될 때 그 베이스 위에 `(+1)` 세대로 쌓인다.

각 GDG 베이스는 다음 공통 속성으로 정의된다.

| 속성 | 값 | 의미 |
|---|---|---|
| `LIMIT(5)` | 5 | 카탈로그에 유지할 최대 세대 수. 5개를 초과하면 가장 오래된 세대가 자동 삭제 처리된다 |
| `SCRATCH` | 지정 | 만료(롤오프)된 세대 데이터셋을 DASD(디스크)에서 물리적으로 삭제. 미지정 시(`NOSCRATCH`) 카탈로그에서만 제거되고 볼륨에 잔류한다 |

각 `DEFINE GENERATIONDATAGROUP` 직후에 나오는 `IF LASTCC=12 THEN SET MAXCC=0`(라인 29, 35, 41, 47, 53, 59)은 **멱등성 처리**다. IDCAMS는 이미 존재하는 GDG를 재정의하려 하면 리턴코드 12(이미 존재)를 낸다. 이 조건문은 해당 오류를 0으로 강제 낮춰 잡 전체가 실패하지 않도록 보호한다. 즉, 이 잡은 **재실행해도 안전하다(idempotent)**.

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|---|---|---|
| `STEP05` | `PGM=IDCAMS` | SYSIN 인라인 제어문으로 6개 GDG 베이스를 순차 정의. `SYSPRINT DD SYSOUT=*`으로 처리 결과를 잡 로그에 출력 |

스텝은 1개뿐이다. IDCAMS는 IBM에서 제공하는 범용 AMS(Access Method Services) 유틸리티로, VSAM/카탈로그 관리 작업을 수행하는 표준 도구다.

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음. 인라인 SYSIN만 사용한다.

- **호출 프로그램 (EXEC PGM)**: `IDCAMS` — IBM 표준 AMS 유틸리티. 메인프레임 시스템 라이브러리(`SYS1.LINKLIB` 또는 `SYS1.MIGLIB`)에 상주한다. 별도 컴파일이나 설치 불필요.

- **데이터셋/파일/DB 테이블**: 아래 6개 GDG 베이스를 **새로 생성**한다. 이 잡 실행 전에는 해당 이름으로 어떤 데이터셋도 존재하지 않아야 정상이나, `IF LASTCC=12 THEN SET MAXCC=0`으로 기존 존재 시에도 안전하게 처리된다.

  | GDG 베이스 이름 | 용도 |
  |---|---|
  | `AWS.M2.CARDDEMO.TRANSACT.BKUP` | 거래 파일 백업 세대 관리 (라인 25) |
  | `AWS.M2.CARDDEMO.TRANSACT.DALY` | 일별 거래 파일 세대 관리 (라인 31) |
  | `AWS.M2.CARDDEMO.TRANREPT` | 거래 리포트 세대 관리 (라인 37) |
  | `AWS.M2.CARDDEMO.TCATBALF.BKUP` | 거래 카테고리 잔액 파일 백업 세대 관리 (라인 43) |
  | `AWS.M2.CARDDEMO.SYSTRAN` | 시스템 거래 파일 세대 관리 (라인 49) |
  | `AWS.M2.CARDDEMO.TRANSACT.COMBINED` | 통합 거래 파일 세대 관리 (라인 55) |

- **선행/후행 잡**:
  - **선행**: 없음. 이 잡은 카탈로그 준비 단계로, 다른 잡에 의존하지 않는다. 단, 마스터 카탈로그 또는 유저 카탈로그가 정상 마운트된 상태여야 한다.
  - **후행**: 위 GDG 베이스에 실제 데이터셋 세대를 만드는 모든 배치 잡이 사후 의존한다. 예:
    - `TRANBKP.jcl` — `AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)` 세대 생성
    - `CBTRN03C` 관련 잡 — `AWS.M2.CARDDEMO.TRANREPT(+1)` 리포트 세대 생성
    - `POSTTRAN.jcl` / `INTCALC.jcl` 등 야간 배치 사이클 — `SYSTRAN`, `TRANSACT.DALY`, `TRANSACT.COMBINED` 세대 소비

---

## Java/현대화 노트

### GDG → Java 개념 매핑

메인프레임 GDG는 현대 시스템에 직접 대응하는 구조물이 없다. 현대화 시 다음 방식 중 하나로 대체를 검토한다.

```java
// GDG LIMIT(5) SCRATCH 의 Java 근사:
// 방법 1 — 파일 시스템 기반 (배치 I/O 유지 시)
// 파일명에 날짜/시퀀스를 붙이고, 5개 초과 시 가장 오래된 파일 삭제
Path baseDir = Path.of("/data/carddemo/transact-bkup");
List<Path> generations = Files.list(baseDir)
    .sorted(Comparator.reverseOrder())
    .collect(Collectors.toList());
if (generations.size() >= 5) {
    Files.delete(generations.get(generations.size() - 1)); // SCRATCH 동작
}

// 방법 2 — 오브젝트 스토리지(S3) 기반 (AWS 현대화 시 권장)
// S3 버전닝 + 라이프사이클 정책으로 LIMIT(5)+SCRATCH 를 선언적으로 구현
// aws s3api put-bucket-lifecycle-configuration 으로 NoncurrentVersionExpiration 5 설정
```

### IDCAMS 역할 현대화

| 메인프레임 | Java/AWS 현대화 대응 |
|---|---|
| `IDCAMS DEFINE GENERATIONDATAGROUP` | S3 버킷 버전닝 활성화 + 라이프사이클 정책 적용 (IaC: Terraform / CloudFormation) |
| `LIMIT(5)` | S3 라이프사이클 `NoncurrentVersionExpiration: Days=N` 또는 최대 버전 수 제한 |
| `SCRATCH` | S3 라이프사이클 `ExpiredObjectDeleteMarker: true` |
| `IF LASTCC=12 THEN SET MAXCC=0` | IaC의 `lifecycle: prevent_destroy = false` 또는 `create_before_destroy` 패턴 |

### 멱등성 패턴 주목

라인 29의 `IF LASTCC=12 THEN SET MAXCC=0`은 JCL 수준의 예외 흡수다. Java/현대 배치 프레임워크(Spring Batch 등)에서는 이를 다음과 같이 표현한다.

```java
// Spring Batch Tasklet 에서 DEFGDGB 역할을 구현할 경우
try {
    catalogService.defineGdgBase(gdgName, limit, scratch);
} catch (GdgAlreadyExistsException e) {
    // IDCAMS LASTCC=12 에 해당 — 이미 존재하면 무시(멱등)
    log.info("GDG base already defined, skipping: {}", gdgName);
}
```

### 이 잡의 위치 (현대화 관점)

이 잡은 **인프라 프로비저닝 단계**에 속한다. 현대화된 환경에서는 애플리케이션 배포 파이프라인(CI/CD)의 "환경 초기화" 단계나 Terraform `apply` 시 한 번만 실행되어야 하며, 야간 배치 사이클과는 분리해서 관리하는 것이 바람직하다.
