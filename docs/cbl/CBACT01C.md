# CBACT01C — 계정 파일 순차 읽기 및 다출력 파일 쓰기 배치

- **유형**: 배치 COBOL
- **한 줄 요약**: VSAM KSDS 계정 파일(ACCTFILE)을 순차적으로 전체 읽어, 고정길이 출력(OUTFILE)·배열 레코드(ARRYFILE)·가변길이 레코드(VBRCFILE) 세 파일에 동시 기록하는 데이터 추출/변환 배치 프로그램.

---

## 기능 설명

CBACT01C는 CardDemo 계정 마스터 파일을 한 번 순회(single-pass sequential scan)하면서, 읽은 계정 레코드를 세 가지 서로 다른 포맷으로 가공해 세 개의 출력 파일에 기록한다.

- **OUTFILE**: 계정의 주요 필드를 평탄화(flatten)한 고정길이 순차 레코드. 재발급일(`ACCT-REISSUE-DATE`)은 외부 어셈블러 서브루틴 `COBDATFT`를 호출해 날짜 형식을 변환한 뒤 기록한다. 현재 사이클 인출(`ACCT-CURR-CYC-DEBIT`)이 0이면 하드코딩된 기본값 `2525.00`으로 치환한다 (라인 236-238).
- **ARRYFILE**: 계정 ID와 `OCCURS 5 TIMES` 배열(`ARR-ACCT-BAL`) 구조의 레코드. 배열 인덱스 1·2에는 실제 잔액을 복사하고, 인덱스 3에는 음수 테스트 데이터(`-1025.00`, `-2500.00`)를 삽입한다. 인덱스 4·5는 `INITIALIZE`로 초기화된 상태로 유지된다 (라인 253-261). **이 파일은 시험/검증 목적 데이터 생성용 파일로 보인다 (추측).**
- **VBRCFILE**: 가변길이 레코드(VB) 파일. 레코드당 두 번 쓴다.
  - VB1 (12바이트): 계정 ID(11) + 활성 상태(1)
  - VB2 (39바이트): 계정 ID(11) + 현재 잔액(12) + 신용한도(12) + 재발급 연도 4자리(4)
- 처리 중 파일 I/O 오류 발생 시 `CEE3ABD`(LE 어보트 API)를 호출해 ABEND코드 999로 비정상 종료한다 (라인 410).

---

## 입력 / 출력

- **입력**:
  - `ACCTFILE` (DD명): VSAM KSDS, 조직=`INDEXED`, 접근=`SEQUENTIAL`, 레코드키=`FD-ACCT-ID(PIC 9(11))`, 레코드길이 300바이트 (FD 정의: 11 + 289 = 300). `ACCOUNT-RECORD` 구조는 copybook `CVACT01Y`에 정의.

- **출력**:
  - `OUTFILE` (DD명): 고정길이 순차 파일. 레코드 구조 = `OUT-ACCT-REC` (소스 라인 57-69). 총 길이 = 11+1+12+12+12+10+10+10+12+12+10 = 112바이트 (단, `OUT-ACCT-CURR-CYC-DEBIT`는 COMP-3 저장이므로 실제 물리 길이에 유의).
  - `ARRYFILE` (DD명): 고정길이 순차 파일. `ARR-ARRAY-REC` 구조 = 계정ID(11) + OCCURS 5 × (잔액 S9(10)V99 DISPLAY 12 + 인출 S9(10)V99 COMP-3 7) + FILLER(4).
  - `VBRCFILE` (DD명): 가변길이(V) 순차 파일. 1계정당 레코드 2개(12바이트·39바이트).
  - **SYSOUT**: 각 계정의 11개 필드를 `DISPLAY`로 표준 출력에 덤프 (라인 201-212).

---

## 의존성

- **COPY (카피북)**:
  - `CVACT01Y` (`app/cpy/CVACT01Y.cpy`): `ACCOUNT-RECORD` 01레벨 정의. 300바이트 KSDS 계정 레코드 레이아웃.
  - `CODATECN` (`app/cpy/CODATECN.cpy`): `CODATECN-REC` 01레벨. 날짜 변환 서브루틴 `COBDATFT`의 연결 데이터 구조(입력 타입/날짜 + 출력 타입/날짜 + 에러 메시지).

- **호출 프로그램 (CALL)**:
  - `COBDATFT` (어셈블러 서브루틴): 날짜 형식 변환. 입력 타입 `'2'`(`YYYY-MM-DD`) → 출력 타입 `'2'`(`YYYYMMDD`) 변환. `CODATECN-REC`를 `USING` 파라미터로 전달 (라인 231). **소스 미포함; 별도 로드 모듈로 링크됨.**
  - `CEE3ABD` (IBM LE 런타임): ABEND 강제 발생. 파라미터: ABCODE=999, TIMING=0 (라인 410).

- **데이터셋/파일/DB 테이블**:
  | DD명 | 조직 | 방향 | 비고 |
  |------|------|------|------|
  | ACCTFILE | VSAM KSDS | INPUT | 계정 마스터 |
  | OUTFILE | QSAM | OUTPUT | 평탄화 계정 레코드 |
  | ARRYFILE | QSAM | OUTPUT | 배열 구조 레코드 |
  | VBRCFILE | QSAM VB | OUTPUT | 가변길이 레코드 |

- **트랜잭션 ID 또는 EXEC PGM**:
  - JCL: `EXEC PGM=CBACT01C`
  - CICS 트랜잭션 없음 (순수 배치).

---

## 핵심 로직 흐름

```
PROCEDURE DIVISION (라인 140)
│
├─ DISPLAY 'START OF EXECUTION...'
├─ 0000-ACCTFILE-OPEN   → OPEN INPUT  ACCTFILE-FILE
├─ 2000-OUTFILE-OPEN    → OPEN OUTPUT OUT-FILE
├─ 3000-ARRFILE-OPEN    → OPEN OUTPUT ARRY-FILE
└─ 4000-VBRFILE-OPEN    → OPEN OUTPUT VBRC-FILE
          ↓
PERFORM UNTIL END-OF-FILE = 'Y'     ← 메인 루프
  │
  └─ 1000-ACCTFILE-GET-NEXT
       │
       ├─ READ ACCTFILE-FILE INTO ACCOUNT-RECORD
       │
       ├─ [STATUS='00'] 정상 읽기
       │    ├─ 1100-DISPLAY-ACCT-RECORD   → DISPLAY 11개 필드
       │    ├─ 1300-POPUL-ACCT-RECORD     → OUT-ACCT-REC 구성
       │    │    └─ CALL 'COBDATFT'        → 날짜 형식 변환
       │    ├─ 1350-WRITE-ACCT-RECORD     → WRITE OUT-ACCT-REC
       │    ├─ 1400-POPUL-ARRAY-RECORD    → ARR-ARRAY-REC 구성(하드코딩 포함)
       │    ├─ 1450-WRITE-ARRY-RECORD     → WRITE ARR-ARRAY-REC
       │    ├─ 1500-POPUL-VBRC-RECORD     → VBRC-REC1/REC2 구성
       │    ├─ 1550-WRITE-VB1-RECORD      → WRITE VBR-REC (12바이트)
       │    └─ 1575-WRITE-VB2-RECORD      → WRITE VBR-REC (39바이트)
       │
       ├─ [STATUS='10'] EOF
       │    └─ APPL-RESULT=16 → APPL-EOF → END-OF-FILE='Y'
       │
       └─ [그 외] I/O 오류
            └─ 9910-DISPLAY-IO-STATUS → 9999-ABEND-PROGRAM → CEE3ABD(999)
          ↓
9000-ACCTFILE-CLOSE  → CLOSE ACCTFILE-FILE
          ↓
DISPLAY 'END OF EXECUTION...'
GOBACK
```

**주목할 세부 동작:**

1. **파일 오픈 에러 처리 패턴** (0000/2000/3000/4000): `APPL-RESULT`를 먼저 8로 설정 후 오픈 → 성공 시 0, 실패 시 12로 갱신 → `APPL-AOK`(level-88, VALUE 0) 조건으로 분기. Java의 `if (result != 0) throw` 패턴과 동일.

2. **날짜 변환** (1300-POPUL-ACCT-RECORD, 라인 223-233):
   - `ACCT-REISSUE-DATE`(X(10), `YYYY-MM-DD` 형식 추정)를 `CODATECN-INP-DATE`에 MOVE.
   - `CODATECN-TYPE='2'`(YYYY-MM-DD 입력), `CODATECN-OUTTYPE='2'`(YYYYMMDD 출력).
   - `COBDATFT` 호출 후 `CODATECN-0UT-DATE` → `OUT-ACCT-REISSUE-DATE` MOVE.
   - **주의**: CODATECN copybook에서 `CODATECN-OUTTYPE VALUE "2"`는 `YYYYMMDD-OP`로 정의되어 있으나, 동시에 `YYYY-MM-DD-OP VALUE "1"`, `YYYYMMDD-OP VALUE "2"`이므로 출력은 `YYYYMMDD` 형식 (라인 38). 즉 `OUT-ACCT-REISSUE-DATE`(X(10))에 8자리 `YYYYMMDD`가 저장되고 나머지 2바이트는 스페이스가 될 가능성 있음.

3. **REDEFINES 활용** (라인 108-110, 137):
   - `TWO-BYTES-BINARY REDEFINES TWO-BYTES-ALPHA`: 2바이트 이진값을 좌/우 1바이트씩 분리해 I/O 상태코드의 두 번째 바이트를 숫자로 변환하는 트릭. Java에는 직접 대응 없음 (`ByteBuffer.wrap(bytes).getShort()` 유사).
   - `WS-REISSUE-DATE REDEFINES WS-ACCT-REISSUE-DATE`: 날짜 구조체를 단순 X(10) 문자열로도 접근 가능하게 함.

4. **가변길이 레코드 쓰기** (1550/1575): `RECORDING MODE IS V` + `WS-RECD-LEN`으로 레코드 길이를 지정. VBR-REC(X(80)) 버퍼의 부분 문자열 표기법 `VBR-REC(1:WS-RECD-LEN)`으로 실제 길이만큼 복사 후 WRITE.

5. **9000-ACCTFILE-CLOSE**: OUTFILE/ARRYFILE/VBRCFILE는 명시적 CLOSE 없음. GOBACK 시 런타임이 자동으로 닫지만, Java로 마이그레이션 시 반드시 명시적 `close()`/try-with-resources 처리 필요.

---

## Java/현대화 노트

### 전체 구조 매핑

```java
// CBACT01C 전체를 Java 배치 Job으로 표현 (Spring Batch 스타일 의사 코드)
public class AccountFileExportJob {

    // WORKING-STORAGE → 인스턴스 필드
    private String endOfFile = "N";           // END-OF-FILE PIC X
    private int    applResult = 0;            // APPL-RESULT S9(9) COMP

    public void run() {
        openFiles();
        while (!"Y".equals(endOfFile)) {
            processNextAccount();
        }
        closeAcctFile();
    }
}
```

### 데이터 타입 변환표

| COBOL 필드 | PIC 정의 | Java 타입 | 비고 |
|-----------|---------|----------|------|
| `ACCT-ID` | `PIC 9(11)` | `long` (또는 `String`) | 11자리 숫자; long 범위 내 |
| `ACCT-ACTIVE-STATUS` | `PIC X(01)` | `char` / `String` | 'Y'/'N' 같은 단일 문자 |
| `ACCT-CURR-BAL` | `PIC S9(10)V99` | `BigDecimal` | 소수점 2자리 묵시적; **float/double 절대 금지** |
| `ACCT-CREDIT-LIMIT` | `PIC S9(10)V99` | `BigDecimal` | 동일 |
| `ACCT-OPEN-DATE` | `PIC X(10)` | `LocalDate` | 형식 확인 후 파싱 |
| `OUT-ACCT-CURR-CYC-DEBIT` | `PIC S9(10)V99 COMP-3` | `BigDecimal` | COMP-3 = packed decimal; 읽기 시 언팩 변환 필요 |
| `ARR-ACCT-CURR-CYC-DEBIT` | `PIC S9(10)V99 COMP-3` | `BigDecimal` | 동일 |
| `ARR-ACCT-BAL OCCURS 5` | 배열 | `List<BalanceEntry>` (또는 `BalanceEntry[]`) | COBOL 배열은 1-based 인덱스임에 주의 |

### 핵심 마이그레이션 주의사항

1. **COMP-3 (packed decimal) 필드**: `OUT-ACCT-CURR-CYC-DEBIT`와 `ARR-ACCT-CURR-CYC-DEBIT`는 COMP-3 저장. 이진 파일을 직접 읽을 경우 packed decimal 언팩 로직이 필요하다. AWS Mainframe Modernization(Micro Focus/Blu Age) 환경에서는 런타임이 자동 변환하지만, 직접 Java 파싱 시 `BigDecimal` 변환 유틸리티를 작성해야 한다.

2. **0 값 기본치 치환 로직** (라인 236-238): `ACCT-CURR-CYC-DEBIT = 0`이면 `2525.00`으로 치환하는 비즈니스 규칙이 소스에 하드코딩되어 있다. Java로 이전 시 이 값을 외부 설정(application.properties)으로 분리할 것을 권장한다.

3. **ARRYFILE 하드코딩 테스트 데이터** (라인 256-260): 인덱스 3에 음수 잔액(`-1025.00`, `-2500.00`)을 삽입하는 코드는 실제 계정 데이터와 무관한 테스트/시뮬레이션 목적으로 추정된다. 운영 마이그레이션 시 제거 여부를 비즈니스팀과 확인해야 한다.

4. **COBDATFT 어셈블러 서브루틴**: Java에서는 `DateTimeFormatter`로 대체 가능하다.
   ```java
   // COBDATFT CALL 대체 예시
   // 입력: "YYYY-MM-DD" → 출력: "YYYYMMDD"
   String input = acctReissueDate.trim();          // CODATECN-INP-DATE
   String output = input.replace("-", "");         // YYYYMMDD
   // 또는:
   LocalDate date = LocalDate.parse(input, DateTimeFormatter.ISO_LOCAL_DATE);
   String output = date.format(DateTimeFormatter.BASIC_ISO_DATE);
   ```

5. **가변길이 레코드(VBRCFILE)**: QSAM VB 포맷은 각 레코드 앞에 4바이트 RDW(Record Descriptor Word)가 붙는 물리 구조다. Java에서 직접 읽으려면 RDW 처리 로직이 필요하다. AWS Glue나 DataStage 같은 ETL 도구 사용을 권장한다.

6. **파일 정상 종료 처리**: OUTFILE/ARRYFILE/VBRCFILE의 CLOSE가 GOBACK 전에 명시적으로 없다. Java try-with-resources 또는 `finally` 블록으로 반드시 자원을 해제해야 한다.

7. **CEE3ABD ABEND**: Java에서는 `throw new RuntimeException("ABEND 999")` 또는 `System.exit(12)`로 대체. Spring Batch라면 `JobExecutionException`을 던져 배치 프레임워크가 FAILED 상태로 기록하게 하는 것이 바람직하다.

8. **DISPLAY 덤프**: 모든 계정 레코드를 SYSOUT에 출력하는 `1100-DISPLAY-ACCT-RECORD`는 운영 환경에서 대량 로그를 발생시킨다. Java 마이그레이션 시 DEBUG 레벨 로그로 변환하고 기본 비활성화를 권장한다.

### 추천 Java 아키텍처

- **Spring Batch** `FlatFileItemReader`(VSAM 대체 후) + `ItemProcessor`(날짜 변환·기본값 치환) + 복수 `FlatFileItemWriter` 또는 `CompositeItemWriter`로 세 출력 파일을 동시에 처리.
- 계정 도메인 객체: `AccountRecord` DTO (CVACT01Y 레이아웃 기반).
- 날짜 변환: `LocalDate` + `DateTimeFormatter`.
- 금액 필드: 모두 `BigDecimal(scale=2)`.
