# CBCUS01C — 고객 파일 순차 읽기 및 출력 배치

- **유형**: 배치 COBOL (QSAM/VSAM KSDS 순차 읽기, CICS 없음)
- **한 줄 요약**: CUSTFILE VSAM KSDS를 처음부터 끝까지 순차 스캔하여 각 고객 레코드를 SYSOUT(표준 출력)으로 DISPLAY하는 단순 열람/검증용 배치 프로그램.

---

## 기능 설명

CBCUS01C는 CardDemo 고객 마스터 파일(CUSTFILE)을 순차 접근(SEQUENTIAL) 방식으로 처음부터 끝까지 읽으면서, 읽힌 고객 레코드를 `DISPLAY` 문으로 JCL SYSOUT에 출력한다. 별도의 업데이트, 쓰기, 집계 연산은 없다. 파일을 열고(`0000-CUSTFILE-OPEN`) → 레코드를 한 건씩 읽어 출력하는 루프(`PERFORM UNTIL END-OF-FILE='Y'`) → 파일을 닫고(`9000-CUSTFILE-CLOSE`) → `GOBACK`으로 종료하는, CB\* 배치 계열의 표준 골격을 가장 단순하게 구현한 예시이다.

오류 처리는 두 단계다. FILE STATUS `'00'`(정상)이면 계속 진행하고, `'10'`(EOF)이면 루프를 종료하며, 그 외 코드는 치명적 오류로 간주해 `Z-DISPLAY-IO-STATUS`로 상태를 로그에 남기고 `Z-ABEND-PROGRAM`(`CALL 'CEE3ABD'`)으로 ABEND 코드 999를 발생시킨다.

---

## 입력 / 출력

- **입력**:
  - `CUSTFILE` — VSAM KSDS, 레코드 길이 500바이트, 키 = `FD-CUST-ID` (9자리 숫자). JCL DD명 `CUSTFILE`로 할당. 순차(SEQUENTIAL) 접근 모드로 열림.
- **출력**:
  - JCL SYSOUT — `DISPLAY CUSTOMER-RECORD` 문으로 각 고객 레코드를 한 줄씩 출력. 파일 쓰기 없음.

---

## 의존성

- **COPY (카피북)**:
  - `CVCUS01Y` (`app/cpy/CVCUS01Y.cpy`) — `CUSTOMER-RECORD` 01레벨 구조를 정의. 총 500바이트. 아래 [데이터 구조](#데이터-구조--java-매핑) 참조.

- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CEE3ABD` — IBM Language Environment(LE) 제공 런타임 루틴. `CALL 'CEE3ABD' USING ABCODE, TIMING`으로 호출해 프로그램을 강제 ABEND 시킨다 (라인 158). Java의 `throw new RuntimeException()` / `System.exit(1)` 에 해당. 소스 파일 없음(시스템 루틴).

- **데이터셋/파일/DB 테이블**:
  - `CUSTFILE` — VSAM KSDS 고객 마스터 (DD명). JCL에서 실제 DSN에 매핑됨. 읽기 전용(INPUT).

- **트랜잭션 ID 또는 EXEC PGM**:
  - `EXEC PGM=CBCUS01C` (JCL 배치 스텝). CICS 트랜잭션 없음. 전용 JCL 파일은 현재 리포지토리에 확인되지 않음 — `run_full_batch.sh` 또는 별도 검증 잡에서 직접 호출될 가능성 있음 (추측).

---

## 핵심 로직 흐름

아래는 `PROCEDURE DIVISION`의 단락 실행 순서를 나타낸다.

```
PROCEDURE DIVISION
│
├─ DISPLAY 'START OF EXECUTION OF PROGRAM CBCUS01C'   ← 라인 71, 시작 로그
│
├─ PERFORM 0000-CUSTFILE-OPEN                          ← 라인 72
│   └─ OPEN INPUT CUSTFILE-FILE
│      FILE STATUS '00' → APPL-RESULT = 0 (AOK)
│      그 외             → APPL-RESULT = 12 → Z-DISPLAY-IO-STATUS → Z-ABEND-PROGRAM
│
├─ PERFORM UNTIL END-OF-FILE = 'Y'                     ← 라인 74~81, 메인 루프
│   └─ IF END-OF-FILE = 'N'
│       └─ PERFORM 1000-CUSTFILE-GET-NEXT
│           ├─ READ CUSTFILE-FILE INTO CUSTOMER-RECORD
│           │   '00' → APPL-RESULT=0, DISPLAY CUSTOMER-RECORD  ← 라인 96 (단락 내)
│           │   '10' → APPL-RESULT=16 (EOF)
│           │   그 외 → APPL-RESULT=12 → abend
│           ├─ APPL-AOK(0) → CONTINUE
│           ├─ APPL-EOF(16) → MOVE 'Y' TO END-OF-FILE  ← 루프 종료 트리거
│           └─ 그 외 → Z-DISPLAY-IO-STATUS → Z-ABEND-PROGRAM
│
│   * 주의: 정상 레코드는 1000-단락 안(라인 96)과 메인 루프(라인 78) 양쪽에서
│            DISPLAY가 수행됨 → 동일 레코드가 두 번 출력됨 (아래 노트 참조)
│
├─ PERFORM 9000-CUSTFILE-CLOSE                         ← 라인 83
│   └─ CLOSE CUSTFILE-FILE
│      FILE STATUS '00' → APPL-RESULT = 0
│      그 외             → APPL-RESULT = 12 → abend
│
├─ DISPLAY 'END OF EXECUTION OF PROGRAM CBCUS01C'     ← 라인 85, 종료 로그
│
└─ GOBACK                                               ← 라인 87, 호출자에 반환
```

### 오류 처리 단락

| 단락 | 역할 |
|------|------|
| `Z-DISPLAY-IO-STATUS` | FILE STATUS 2바이트를 사람이 읽기 쉬운 형태로 DISPLAY. 수치형이 아니거나 STAT1='9'이면 드라이버 반환코드(IO-STAT2를 `TWO-BYTES-BINARY`로 변환 후 숫자로 표시), 그 외엔 표준 2자리 코드를 포맷해 출력. |
| `Z-ABEND-PROGRAM` | `CALL 'CEE3ABD' USING ABCODE(999), TIMING(0)` — LE 강제 종료. JCL에 ABEND S999 또는 U999로 기록됨. |

### 이중 DISPLAY 버그 (라인 78 vs 96)

`1000-CUSTFILE-GET-NEXT` 단락 내 라인 96에서 `DISPLAY CUSTOMER-RECORD`를 실행하고, 호출부(`PERFORM 1000-CUSTFILE-GET-NEXT`) 직후 라인 78~79에서 `END-OF-FILE = 'N'`이면 다시 `DISPLAY CUSTOMER-RECORD`를 수행한다. 정상 레코드 한 건당 두 번 출력되는 구조다. 이는 의도적인 중복(점검용)이거나 개발 과정에서 생긴 잔류 코드로 보인다 (추측). Java 이식 시 하나만 남기도록 정리 필요.

---

## 데이터 구조 / Java 매핑

### FD CUSTFILE-FILE (FILE SECTION, 라인 38~40)

파일 레코드 버퍼. `1000-CUSTFILE-GET-NEXT`의 `READ ... INTO CUSTOMER-RECORD` 문이 이 버퍼에서 WORKING-STORAGE의 `CUSTOMER-RECORD`로 복사한다.

| COBOL 필드 | PIC | 바이트 | 역할 |
|-----------|-----|--------|------|
| `FD-CUST-ID` | `9(09)` | 9 | KSDS 키 (DISPLAY 숫자) |
| `FD-CUST-DATA` | `X(491)` | 491 | 나머지 고객 데이터 블록 |
| 합계 | | **500** | RECLN 500 |

### CUSTOMER-RECORD (CVCUS01Y.cpy, 라인 4~23)

`READ ... INTO CUSTOMER-RECORD`로 채워지는 작업 영역. KSDS의 실제 레이아웃과 동일.

| COBOL 필드 | PIC | 바이트 | Java 타입 제안 |
|-----------|-----|--------|---------------|
| `CUST-ID` | `9(09)` | 9 | `int` 또는 `String` (선행 0 보존 필요 시 `String`) |
| `CUST-FIRST-NAME` | `X(25)` | 25 | `String` (우측 공백 trim 필요) |
| `CUST-MIDDLE-NAME` | `X(25)` | 25 | `String` |
| `CUST-LAST-NAME` | `X(25)` | 25 | `String` |
| `CUST-ADDR-LINE-1` | `X(50)` | 50 | `String` |
| `CUST-ADDR-LINE-2` | `X(50)` | 50 | `String` |
| `CUST-ADDR-LINE-3` | `X(50)` | 50 | `String` |
| `CUST-ADDR-STATE-CD` | `X(02)` | 2 | `String` (2글자 주 코드) |
| `CUST-ADDR-COUNTRY-CD` | `X(03)` | 3 | `String` (ISO 3166-1 alpha-3) |
| `CUST-ADDR-ZIP` | `X(10)` | 10 | `String` |
| `CUST-PHONE-NUM-1` | `X(15)` | 15 | `String` |
| `CUST-PHONE-NUM-2` | `X(15)` | 15 | `String` |
| `CUST-SSN` | `9(09)` | 9 | `String` (민감정보, 숫자지만 선행 0 가능) |
| `CUST-GOVT-ISSUED-ID` | `X(20)` | 20 | `String` |
| `CUST-DOB-YYYY-MM-DD` | `X(10)` | 10 | `LocalDate` (파싱 후) |
| `CUST-EFT-ACCOUNT-ID` | `X(10)` | 10 | `String` |
| `CUST-PRI-CARD-HOLDER-IND` | `X(01)` | 1 | `boolean` / `char` (`'Y'`/`'N'`) |
| `CUST-FICO-CREDIT-SCORE` | `9(03)` | 3 | `int` (300~850 범위) |
| `FILLER` | `X(168)` | 168 | 미사용 패딩 |
| 합계 | | **500** | |

모든 `PIC X(n)` 필드는 EBCDIC 고정 길이 문자열로 저장된다. Java 이식 시 EBCDIC→UTF-8 디코딩(IBM Cp037 또는 Cp1047 코드페이지)과 우측 공백 `trim()` 처리가 필요하다.

`PIC 9(n)` DISPLAY 숫자는 EBCDIC 숫자 문자(F0~F9)로 저장된다. COMP 또는 COMP-3 지정이 없으므로 모두 DISPLAY 형식 — 바이트 수 = 자릿수.

### WORKING-STORAGE 주요 변수

| 변수 | PIC / 타입 | 용도 |
|------|-----------|------|
| `CUSTFILE-STATUS` (STAT1+STAT2) | `PIC X` × 2 | VSAM FILE STATUS 2바이트 |
| `APPL-RESULT` | `PIC S9(9) COMP` | 내부 처리 결과 코드 |
| `APPL-AOK` (88레벨) | VALUE 0 | APPL-RESULT = 0 조건 → `boolean` |
| `APPL-EOF` (88레벨) | VALUE 16 | APPL-RESULT = 16 조건 → `boolean` |
| `END-OF-FILE` | `PIC X(01)` | `'Y'`/`'N'` 플래그 → `boolean` |
| `TWO-BYTES-BINARY` + `TWO-BYTES-ALPHA` | REDEFINES 쌍 | 오류 상태 포맷용 바이트 조작 (아래 참조) |
| `ABCODE` | `PIC S9(9) BINARY` | CEE3ABD에 넘기는 ABEND 코드 (999) |
| `TIMING` | `PIC S9(9) BINARY` | CEE3ABD 타이밍 파라미터 (0) |

#### TWO-BYTES-BINARY / TWO-BYTES-ALPHA REDEFINES (라인 53~56)

```cobol
01  TWO-BYTES-BINARY        PIC 9(4) BINARY.
01  TWO-BYTES-ALPHA         REDEFINES TWO-BYTES-BINARY.
    05  TWO-BYTES-LEFT      PIC X.
    05  TWO-BYTES-RIGHT     PIC X.
```

동일한 2바이트 메모리 영역을 두 가지 방식으로 해석한다. `TWO-BYTES-BINARY`로 쓰면 빅엔디언 unsigned 정수(0~9999), `TWO-BYTES-ALPHA`로 읽으면 각각 1바이트씩 독립 문자. Java에는 직접 대응하는 구문이 없다. 이 용도는 `Z-DISPLAY-IO-STATUS`에서 FILE STATUS STAT2 바이트를 정수로 변환하기 위한 것이다.

```java
// Java 동등 구현 예시
byte[] twoBytes = new byte[2];
twoBytes[1] = io_stat2;          // TWO-BYTES-RIGHT ← IO-STAT2
int twoBytesAsInt = ((twoBytes[0] & 0xFF) << 8) | (twoBytes[1] & 0xFF);
// = TWO-BYTES-BINARY
```

---

## Java/현대화 노트

### 1. Spring Batch 매핑

이 프로그램의 전체 구조는 Spring Batch `Job` → `Step` → `FlatFileItemReader` + `ItemWriter(log)` 로 직관적으로 대응된다.

```java
@Bean
public Job customerPrintJob(JobRepository jobRepository, Step step) {
    return new JobBuilder("customerPrintJob", jobRepository)
            .start(step).build();
}

@Bean
public Step step(JobRepository jobRepository,
                 PlatformTransactionManager txMgr,
                 ItemReader<CustomerRecord> reader) {
    return new StepBuilder("printStep", jobRepository)
            .<CustomerRecord, CustomerRecord>chunk(100, txMgr)
            .reader(reader)
            .writer(items -> items.forEach(r -> log.info("{}", r)))
            .build();
}

@Bean
public FlatFileItemReader<CustomerRecord> custFileReader(
        @Value("${custfile.path}") Resource resource) {
    return new FixedLengthTokenizerItemReaderBuilder<CustomerRecord>()
            .resource(resource)
            .encoding("IBM037")   // EBCDIC 코드페이지
            .recordLength(500)
            .lineMapper(new CustomerRecordLineMapper())
            .build();
}
```

KSDS는 VSAM이므로 실제 마이그레이션에서는 DB 테이블(예: PostgreSQL `customers`)로 치환하고 `JdbcCursorItemReader`를 사용하는 것이 일반적이다.

### 2. EBCDIC 고정 길이 문자열

메인프레임에서 내보낸 바이너리 파일을 Java에서 읽을 때 반드시 `Charset.forName("IBM037")` 또는 `"Cp1047"`로 디코딩해야 한다. ASCII로 읽으면 숫자 필드를 포함한 전 필드가 오염된다.

### 3. SSN 등 민감 필드

`CUST-SSN` (`9(09)`) 은 마스킹 없이 그대로 DISPLAY된다. Java 이식 시 로그 출력 정책(마스킹, 암호화)을 별도로 설계해야 한다. PCI-DSS / GDPR 준수 여부 검토 필요.

### 4. 이중 DISPLAY 버그

앞서 설명한 대로 `1000-CUSTFILE-GET-NEXT` 내부(라인 96)와 메인 루프(라인 78) 두 곳에서 `DISPLAY CUSTOMER-RECORD`가 실행된다. Java 이식 시 로그 출력은 한 곳에서만 수행하도록 정리해야 한다.

### 5. Level-88 조건명 → boolean 메서드

```cobol
88  APPL-AOK   VALUE 0.
88  APPL-EOF   VALUE 16.
```

Java 이식 시:

```java
boolean isAok()  { return applResult == 0; }
boolean isEof()  { return applResult == 16; }
```

### 6. GOBACK vs STOP RUN

`GOBACK`(라인 87)은 호출 계층에 관계없이 "이 프로그램의 호출자"로 돌아간다. 최상위 JCL에서 실행되면 STOP RUN과 동일하게 작동한다. Java의 메서드 `return`과 같은 의미지만, 이 프로그램은 `main`에 해당하므로 `System.exit(0)`에 대응된다.

### 7. CEE3ABD — 강제 ABEND

`CALL 'CEE3ABD' USING ABCODE(999), TIMING(0)`은 IBM LE의 강제 종료 API다. Java에서는 다음처럼 표현할 수 있다.

```java
// COBOL: CALL 'CEE3ABD' USING ABCODE, TIMING
throw new BatchCriticalException("FILE STATUS: " + ioStatus, exitCode);
// 또는
System.exit(999);
```

Spring Batch에서는 `StepExecution.setTerminateOnly()` + `ExitStatus.FAILED`를 조합해 잡을 중단시키는 것이 관례적이다.

### 8. COMP vs DISPLAY 숫자

| COBOL | 저장 방식 | Java 타입 |
|-------|----------|----------|
| `PIC 9(09)` (DISPLAY) | 9바이트 EBCDIC 숫자 문자 | `int` / `long` |
| `PIC S9(9) COMP` | 4바이트 빅엔디언 부호 이진수 | `int` |
| `PIC S9(9) BINARY` | 4바이트 빅엔디언 부호 이진수 | `int` |

`COMP`와 `BINARY`는 IBM Enterprise COBOL에서 동의어다. `APPL-RESULT`, `ABCODE`, `TIMING`이 이 형식이다.
