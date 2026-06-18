# READCUST — 고객 마스터 파일 읽기/덤프 잡

- **유형**: JCL (배치 잡)
- **한 줄 요약**: VSAM KSDS 고객 마스터 파일(`CUSTDATA`)을 순차 읽기하여 모든 레코드를 SYSOUT으로 출력하는 단일 스텝 진단/덤프 잡

---

## 기능 설명

`READCUST`는 `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` 파일의 전체 고객 레코드를 처음부터 끝까지 순차적으로 읽고, 각 레코드를 `DISPLAY`(SYSOUT)로 출력하는 단순 덤프 잡이다.

호출되는 프로그램 `CBCUS01C`의 로직 흐름은 다음과 같다:

1. **OPEN** — `0000-CUSTFILE-OPEN`: CUSTFILE-FILE을 `INPUT`으로 연다. 파일 STATUS가 `'00'`이 아니면 즉시 `Z-ABEND-PROGRAM`으로 강제 종료(`CALL 'CEE3ABD'`, 종료코드 999).
2. **READ LOOP** — `PERFORM UNTIL END-OF-FILE = 'Y'`: `1000-CUSTFILE-GET-NEXT`로 레코드를 한 건씩 읽어 `CUSTOMER-RECORD`(copybook `CVCUS01Y`)로 받은 뒤 `DISPLAY`로 출력.
   - STATUS `'00'` → 정상, `APPL-RESULT = 0` (level-88 `APPL-AOK`)
   - STATUS `'10'` → EOF, `APPL-RESULT = 16` (level-88 `APPL-EOF`) → `END-OF-FILE = 'Y'`로 루프 종료
   - 그 외 → 오류 메시지 + `Z-DISPLAY-IO-STATUS` + abend
3. **CLOSE** — `9000-CUSTFILE-CLOSE`: 파일을 닫는다.
4. **GOBACK** — 정상 종료.

출력 내용은 `CUSTOMER-RECORD` 500바이트 전체(EBCDIC 텍스트)이며, 사람이 읽을 수 있는 형태로 출력된다. 운영 배치 파이프라인에는 포함되지 않는 독립 진단 잡이다.

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|--------------|------|
| `STEP05` | `PGM=CBCUS01C` | CUSTDATA VSAM KSDS를 순차 스캔, 전 레코드 SYSOUT 덤프 |

> 스텝이 하나뿐인 단순 잡이다. `STEP05`라는 이름은 CardDemo 내 여러 JCL에서 관례적으로 사용하는 첫 번째 유효 스텝 번호다(근거: JCL 21번 라인 `//STEP05 EXEC PGM=CBCUS01C`).

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 — 이 JCL은 외부 PROC이나 INCLUDE를 참조하지 않는다.

- **호출 프로그램 (EXEC PGM)**:
  - `CBCUS01C` (`app/cbl/CBCUS01C.cbl`) — 고객 파일 읽기/덤프 배치 프로그램. 로드모듈은 `AWS.M2.CARDDEMO.LOADLIB`에서 가져온다(JCL 22~23번 라인 `//STEPLIB DD DISP=SHR, DSN=AWS.M2.CARDDEMO.LOADLIB`).

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.LOADLIB` (DISP=SHR) — COBOL 로드 라이브러리(실행 모듈)
  - `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` (DISP=SHR, DD명 `CUSTFILE`) — 고객 마스터 VSAM KSDS, 레코드 길이 500바이트, 키=`CUST-ID PIC 9(09)` 9바이트. copybook `CVCUS01Y`가 레이아웃 정의(JCL 24~25번 라인).
  - `SYSOUT=*` (DD명 `SYSOUT`, `SYSPRINT`) — JES 스풀 출력. `SYSOUT`은 `DISPLAY` 덤프 수신, `SYSPRINT`는 컴파일러/런타임 메시지 수신.

- **선행/후행 잡**:
  - 선행 잡: 야간 배치 시퀀스(CLOSEFIL → 마스터 파일 로드/갱신)가 완료된 후 실행하는 것이 안전하다. CUSTFILE VSAM은 CICS 온라인 처리와 공유되므로, CICS가 파일을 열고 있는 상태에서 이 잡을 병행 실행하면 ENQUEUE 충돌이 발생할 수 있다(추측 — DISP=SHR이므로 읽기 공유는 허용되나 실제 환경 설정에 따라 다름).
  - 후행 잡: 없음. 순수 읽기 전용 덤프 잡이므로 이후 처리 잡을 트리거하지 않는다.
  - 야간 배치 공식 시퀀스(POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANIDX → OPENFIL)에는 포함되지 않는 독립 진단 잡이다.

---

## Java/현대화 노트

### 1. VSAM KSDS → JPA Repository / JDBC

COBOL에서 `SELECT CUSTFILE-FILE ASSIGN TO CUSTFILE ORGANIZATION IS INDEXED ACCESS MODE IS SEQUENTIAL`는 키 순서로 전체 테이블을 커서 스캔하는 것과 동일하다. Java 마이그레이션 대응:

```java
// VSAM KSDS 순차 스캔 → Spring Data JPA 전체 조회
List<Customer> customers = customerRepository.findAll(Sort.by("custId"));
customers.forEach(c -> System.out.println(c));  // DISPLAY 대응
```

### 2. FILE STATUS 2바이트 패턴 → checked exception

COBOL의 FILE STATUS `'00'`/`'10'`/기타 분기는 Java의 try-catch와 대응한다:

```java
// COBOL: IF CUSTFILE-STATUS = '10' → EOF
// Java 대응
try (var stream = customerRepository.findAllAsStream()) {
    stream.forEach(this::displayRecord);
} catch (DataAccessException e) {
    log.error("ERROR READING CUSTOMER FILE");
    throw new RuntimeException("abend: " + e.getMessage(), e);
}
```

### 3. level-88 조건명 → boolean 필드 / enum

```cobol
01  APPL-RESULT  PIC S9(9) COMP.
    88  APPL-AOK  VALUE 0.
    88  APPL-EOF  VALUE 16.
```
→ Java에서는 `enum AppResult { AOK(0), EOF(16), ERROR(12) }`로 표현하거나 단순히 상수 `int`로 처리한다.

### 4. CEE3ABD 강제 abend → System.exit / RuntimeException

`CALL 'CEE3ABD' USING ABCODE, TIMING`(ABCODE=999)은 Language Environment의 강제 비정상 종료다. Java 배치(Spring Batch)에서는 `Step`의 `ExitStatus.FAILED`를 반환하거나 `SystemExit`를 호출해 JCL의 COND 코드 체계와 연동한다.

### 5. CUSTOMER-RECORD 레이아웃 (copybook CVCUS01Y)

| COBOL 필드 | PIC | 길이 | Java 타입 |
|-----------|-----|------|-----------|
| `CUST-ID` | `9(09)` | 9 | `long` (또는 `String` — 선행 0 보존 필요 시) |
| `CUST-FIRST-NAME` | `X(25)` | 25 | `String` (trim 필요) |
| `CUST-MIDDLE-NAME` | `X(25)` | 25 | `String` (trim 필요) |
| `CUST-LAST-NAME` | `X(25)` | 25 | `String` (trim 필요) |
| `CUST-ADDR-LINE-1~3` | `X(50)` each | 50×3 | `String` |
| `CUST-ADDR-STATE-CD` | `X(02)` | 2 | `String` (ISO 3166-2) |
| `CUST-ADDR-COUNTRY-CD` | `X(03)` | 3 | `String` (ISO 3166-1) |
| `CUST-ADDR-ZIP` | `X(10)` | 10 | `String` |
| `CUST-PHONE-NUM-1/2` | `X(15)` each | 15 | `String` |
| `CUST-SSN` | `9(09)` | 9 | `String` (개인정보 — 숫자지만 String 권장) |
| `CUST-GOVT-ISSUED-ID` | `X(20)` | 20 | `String` |
| `CUST-DOB-YYYY-MM-DD` | `X(10)` | 10 | `LocalDate` (파싱 후) |
| `CUST-EFT-ACCOUNT-ID` | `X(10)` | 10 | `String` |
| `CUST-PRI-CARD-HOLDER-IND` | `X(01)` | 1 | `boolean` (`'Y'`/`'N'`) |
| `CUST-FICO-CREDIT-SCORE` | `9(03)` | 3 | `int` (300~850) |
| `FILLER` | `X(168)` | 168 | (무시) |
| **합계** | | **500** | |

> COBOL `PIC X(n)` 필드는 EBCDIC에서 공백(0x40)으로 패딩된 고정 길이 문자열이다. Java로 변환 시 반드시 `String.trim()` 또는 EBCDIC→UTF-8 변환(cp037 코덱) 후 trim이 필요하다.

### 6. 이 잡의 현대화 목적

이 잡은 운영 로직이 없는 **데이터 검증/확인용 유틸리티**다. 현대화 시에는 Spring Batch `ItemReader<Customer>` + `ItemWriter`(콘솔/파일 출력)로 구현하거나, 단순히 DB 조회 API 엔드포인트 + 로그로 대체할 수 있다.
