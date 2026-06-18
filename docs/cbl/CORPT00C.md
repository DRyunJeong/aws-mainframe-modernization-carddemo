# CORPT00C — 거래 리포트 출력 요청 (CICS 온라인 JCL 제출)

- **유형**: CICS 온라인 COBOL
- **한 줄 요약**: 사용자가 Monthly / Yearly / Custom 중 리포트 유형과 날짜 범위를 선택하면, JCL 텍스트를 내부 리더 TDQ(`JOBS`)에 기록하여 TRANREPT 배치 잡을 온라인 세션에서 직접 제출한다.

---

## 기능 설명

CORPT00C는 CICS 3270 단말 사용자가 거래 명세 리포트(CBTRN03C 배치 리포트)의 인쇄를 요청할 수 있는 온라인 화면 프로그램이다. 사용자는 세 가지 리포트 유형 중 하나를 선택한다.

| 유형 | 날짜 범위 자동 계산 |
|------|------------------|
| Monthly | 현재 달 1일 ~ 말일 (FUNCTION CURRENT-DATE + INTEGER-OF-DATE 활용) |
| Yearly | 현재 연도 01-01 ~ 12-31 |
| Custom | 사용자가 직접 시작/종료 연·월·일 입력, `CSUTLDTC` 서브루틴으로 유효성 검증 |

날짜 범위가 확정되면 프로그램은 WORKING-STORAGE에 미리 정의된 JCL 골격(`JOB-DATA-1`, 80바이트 고정 레코드 × 최대 1000줄)에 날짜 파라미터를 삽입하고, 레코드를 한 줄씩 `EXEC CICS WRITEQ TD QUEUE('JOBS')`로 기록한다. `JOBS`는 Extra-Partition TDQ로, CICS가 소비하는 내부 리더(Intrinsic Reader)에 연결되어 있어 기록 즉시 JES에 잡이 제출된다.

확인 필드(`CONFIRMI`)에 `Y`/`y`를 입력해야 실제 제출이 진행되며, `N`/`n`을 입력하면 화면을 초기화하고 취소한다.

---

## 입력 / 출력

- **입력**:
  - CICS COMMAREA (`CARDDEMO-COMMAREA`, COCOM01Y.cpy) — 프로그램 재진입 여부 및 네비게이션 컨텍스트
  - BMS 맵 입력 구조체 `CORPT0AI` (CORPT00.CPY / cpy-bms) — 사용자 입력 필드
    - `MONTHLYI` (PIC X(1)) — Monthly 선택 마커
    - `YEARLYI` (PIC X(1)) — Yearly 선택 마커
    - `CUSTOMI` (PIC X(1)) — Custom 선택 마커
    - `SDTMMI`/`SDTDDI`/`SDTYYYYI` (PIC X(2/2/4)) — 시작 날짜
    - `EDTMMI`/`EDTDDI`/`EDTYYYYI` (PIC X(2/2/4)) — 종료 날짜
    - `CONFIRMI` (PIC X(1)) — 제출 확인 (`Y`/`N`)
  - `EIBAID` / `EIBCALEN` — CICS EIB 시스템 필드 (DFHAID.cpy)

- **출력**:
  - BMS 맵 출력 구조체 `CORPT0AO` (CORPT00.CPY / cpy-bms REDEFINES) — 화면 렌더링
    - 헤더(날짜, 시간, 트랜잭션명, 프로그램명), 에러 메시지(`ERRMSGO`)
  - Extra-Partition TDQ `JOBS` — JCL 레코드 스트림 (80바이트 × N줄)
    - CICS 내부 리더가 이를 읽어 JES에 `TRNRPT00` 잡 제출
  - CICS COMMAREA 반환 (RETURN TRANSID `CR00`)

---

## 의존성

- **COPY (카피북)**:

  | 카피북 | 경로 | 용도 |
  |--------|------|------|
  | `COCOM01Y` | app/cpy/COCOM01Y.cpy | `CARDDEMO-COMMAREA` 공통 통신 영역 |
  | `CORPT00` | app/cpy-bms/CORPT00.CPY | BMS 맵 `CORPT0AI`(입력) / `CORPT0AO`(출력) |
  | `COTTL01Y` | app/cpy/COTTL01Y.cpy | 화면 타이틀 문자열 (`CCDA-TITLE01`, `CCDA-TITLE02`) |
  | `CSDAT01Y` | app/cpy/CSDAT01Y.cpy | `WS-DATE-TIME` — 현재 날짜·시간 구조체 |
  | `CSMSG01Y` | app/cpy/CSMSG01Y.cpy | `CCDA-COMMON-MESSAGES` — 공통 에러 메시지 |
  | `CVTRA05Y` | app/cpy/CVTRA05Y.cpy | `TRAN-RECORD` (350바이트) — 본 프로그램에서 직접 참조하지 않으나 COPY로 포함됨 |
  | `DFHAID` | CICS 시스템 | `DFHENTER`, `DFHPF3` 등 AID 키 상수 |
  | `DFHBMSCA` | CICS 시스템 | `DFHGREEN` 등 BMS 컬러 속성 상수 |

- **호출 프로그램 (CALL/XCTL/LINK)**:

  | 방식 | 대상 | 조건 |
  |------|------|------|
  | `CALL` | `CSUTLDTC` | Custom 모드에서 날짜 유효성 검증 |
  | `EXEC CICS XCTL` | `COMEN01C` | PF3 — 메뉴로 복귀 |
  | `EXEC CICS XCTL` | `COSGN00C` | EIBCALEN=0 또는 TO-PROGRAM 미설정 시 로그인 화면으로 복귀 |

- **데이터셋/파일/DB 테이블**:
  - Extra-Partition TDQ `JOBS` (CICS TCT/TDCT에 정의) — 내부 리더 큐
  - 직접 파일 I/O 없음. 잡 제출 후 배치 단계에서 접근하는 파일은 TRANREPT 프로시저 참조:
    - `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` (입력 거래 VSAM)
    - `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS`, `TRANTYPE.VSAM.KSDS`, `TRANCATG.VSAM.KSDS` (룩업)
    - `AWS.M2.CARDDEMO.DATEPARM` (날짜 파라미터 파일 — 잡 내 인라인 DD로 생성)
    - `AWS.M2.CARDDEMO.TRANREPT(+1)` (리포트 출력, GDG)

- **트랜잭션 ID 또는 EXEC PGM**:
  - 트랜잭션 ID: `CR00` (WS-TRANID, 라인 38)
  - EXEC PGM: 해당 없음 (온라인 CICS 프로그램)
  - 제출되는 잡의 PROC: `TRANREPT` (app/proc/TRANREPT.prc) → 내부적으로 `CBTRN03C` 실행

---

## 핵심 로직 흐름

### 1단계 — MAIN-PARA 진입 분기 (라인 163–202)

```
EIBCALEN = 0 ?
  YES → XCTL to COSGN00C  (세션 없음, 로그인 화면으로)
  NO  → COMMAREA 복사
        CDEMO-PGM-REENTER = FALSE ?
          YES (첫 진입) → REENTER 플래그 ON, 화면 초기화 후 SEND-TRNRPT-SCREEN
          NO  (재진입)  → RECEIVE-TRNRPT-SCREEN
                          EVALUATE EIBAID
                            DFHENTER → PROCESS-ENTER-KEY
                            DFHPF3   → XCTL to COMEN01C
                            OTHER    → 에러 메시지 + SEND-TRNRPT-SCREEN
EXEC CICS RETURN TRANSID(CR00) COMMAREA(...)
```

이 흐름은 모든 CO\* 온라인 프로그램이 공유하는 pseudo-conversational 패턴이다 (메모리: `online_pseudoconv_pattern.md`).

### 2단계 — PROCESS-ENTER-KEY (라인 208–456)

`EVALUATE TRUE`로 세 가지 경로를 처리한다.

**경로 A: Monthly (라인 213–238)**

```cobol
FUNCTION CURRENT-DATE → WS-CURDATE-DATA
WS-START-DATE = YYYY-MM-01  (이번달 1일)
WS-END-DATE   = 다음달 1일 - 1일
  (COMPUTE WS-CURDATE-N = FUNCTION DATE-OF-INTEGER(
           FUNCTION INTEGER-OF-DATE(WS-CURDATE-N) - 1))
PARM-START-DATE-1 / PARM-START-DATE-2 ← WS-START-DATE
PARM-END-DATE-1   / PARM-END-DATE-2   ← WS-END-DATE
PERFORM SUBMIT-JOB-TO-INTRDR
```

> 주의: `WS-CURDATE-N`은 `CSDAT01Y`의 REDEFINES 필드로, `WS-CURDATE`(YYYYMMDD 8자리)를 PIC 9(08) 숫자로 오버레이해 `INTEGER-OF-DATE`/`DATE-OF-INTEGER` 함수에 직접 전달한다. Java로는 `LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)` 한 줄로 표현할 수 있다.

**경로 B: Yearly (라인 239–255)**

```cobol
WS-START-DATE = YYYY-01-01
WS-END-DATE   = YYYY-12-31
PERFORM SUBMIT-JOB-TO-INTRDR
```

**경로 C: Custom (라인 256–436)**

1. 입력 필드 공백/LOW-VALUES 검증 (6개 필드, 라인 259–300)
2. `FUNCTION NUMVAL-C`로 문자→숫자 파싱 후 되돌려 씀 (라인 305–327)
3. 월(≤12), 일(≤31), 숫자 여부 범위 검증 (라인 329–378)
4. `CALL 'CSUTLDTC'`로 시작일 캘린더 유효성 검증 (라인 392–406)
5. `CALL 'CSUTLDTC'`로 종료일 캘린더 유효성 검증 (라인 408–426)
   - 반환 코드 `CSUTLDTC-RESULT-SEV-CD = '0000'`이면 유효
   - 메시지 번호 `2513`은 허용(추측: 미래 날짜 경고)
6. 검증 통과 시 `PERFORM SUBMIT-JOB-TO-INTRDR`

오류 발생 시 매 검증마다 `PERFORM SEND-TRNRPT-SCREEN`을 즉시 호출하고 `GO TO RETURN-TO-CICS`로 제어를 넘긴다 (SEND-TRNRPT-SCREEN 맨 끝에 고정된 `GO TO RETURN-TO-CICS` 라인 580).

### 3단계 — SUBMIT-JOB-TO-INTRDR (라인 462–510)

```
CONFIRMI = SPACES? → 확인 요청 메시지 표시 후 대기
CONFIRMI = 'Y'/'y' → 계속
CONFIRMI = 'N'/'n' → 화면 초기화, ERR-FLG ON, 재표시(취소)
CONFIRMI = 기타   → 에러 메시지

PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > 1000
                                       OR END-LOOP-YES
                                       OR ERR-FLG-ON
  JCL-RECORD ← JOB-LINES(WS-IDX)
  JCL-RECORD = '/*EOF' or SPACES → END-LOOP-YES
  PERFORM WIRTE-JOBSUB-TDQ
END-PERFORM
```

`JOB-DATA-1`은 80바이트 FILLER들로 구성된 인라인 JCL 템플릿이다 (라인 82–125). 날짜가 삽입되는 위치는 두 곳이다.

- `FILLER-1` / `PARM-START-DATE-1` (라인 103–107): SORT SYMNAMES의 `PARM-START-DATE,C'YYYY-MM-DD'`
- `FILLER-2` / `PARM-END-DATE-1` (라인 108–112): SORT SYMNAMES의 `PARM-END-DATE,C'YYYY-MM-DD'`
- `FILLER-3` / `PARM-START-DATE-2` + `PARM-END-DATE-2` (라인 117–121): `DATEPARM DD *` 인라인 데이터

`JOB-DATA-2 REDEFINES JOB-DATA-1`으로 동일 메모리를 1000개 배열(`JOB-LINES OCCURS 1000 TIMES PIC X(80)`)로 재해석하여 반복 루프에서 인덱스 접근한다 (라인 126–127).

### 4단계 — WIRTE-JOBSUB-TDQ (라인 515–535)

```cobol
EXEC CICS WRITEQ TD
  QUEUE ('JOBS')
  FROM (JCL-RECORD)
  LENGTH (LENGTH OF JCL-RECORD)
  RESP(WS-RESP-CD)
END-EXEC
```

`JOBS` TDQ가 내부 리더에 연결되어 있으므로, 마지막 레코드가 기록되는 순간 JES가 잡을 픽업해 스케줄링한다. RESP가 DFHRESP(NORMAL)이 아니면 에러 메시지와 함께 화면 재표시.

### 5단계 — 제출 성공 후 (라인 445–456)

```cobol
PERFORM INITIALIZE-ALL-FIELDS
MOVE DFHGREEN TO ERRMSGC OF CORPT0AO
STRING WS-REPORT-NAME DELIMITED BY SPACE
       ' report submitted for printing ...' ...
       INTO WS-MESSAGE
PERFORM SEND-TRNRPT-SCREEN
```

화면 필드를 모두 초기화하고 녹색 글씨로 성공 메시지를 출력한다.

---

## Java/현대화 노트

### 1. Extra-Partition TDQ → JMS/SQS/비동기 잡 제출

CICS Extra-Partition TDQ `JOBS`는 내부 리더에 연결된 메시지 큐다. Java 현대화 시에는 다음으로 대체할 수 있다.

```java
// AWS 환경: SQS 메시지로 배치 잡 파라미터 전달
sqsClient.sendMessage(SendMessageRequest.builder()
    .queueUrl(BATCH_JOB_QUEUE_URL)
    .messageBody(objectMapper.writeValueAsString(new ReportRequest(
        reportType, startDate, endDate)))
    .build());
```

JCL 텍스트를 직접 큐에 쓰는 방식 대신, 파라미터 객체를 직렬화해 AWS Batch/Step Functions 등 배치 오케스트레이터에 전달하는 방식이 현대적이다.

### 2. REDEFINES를 이용한 배열 오버레이

```cobol
01 JOB-DATA.
  02 JOB-DATA-1.
     05 FILLER PIC X(80) VALUE "//TRNRPT00 JOB ...".
     ...       (총 N개 레코드)
  02 JOB-DATA-2 REDEFINES JOB-DATA-1.
     05 JOB-LINES OCCURS 1000 TIMES PIC X(80).
```

동일 메모리 블록을 선형 문자열과 배열로 동시에 해석하는 C `union`과 같은 패턴이다. Java에는 직접 동등물이 없다. 현대화 시에는 `List<String>` 또는 `String[]`로 표현하고, 날짜 파라미터는 문자열 포맷팅으로 삽입한다.

```java
List<String> jclLines = buildJclTemplate(startDate, endDate);
```

### 3. CSUTLDTC 날짜 검증 서브루틴

`CALL 'CSUTLDTC'`는 날짜 문자열과 포맷을 받아 유효성을 반환하는 유틸리티 서브루틴이다. Java에서는 다음으로 대체한다.

```java
try {
    LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
} catch (DateTimeParseException e) {
    // 유효하지 않은 날짜
}
```

메시지 번호 `2513` 예외 처리(라인 399, 419)는 해당 서브루틴 내부 코드이므로 Java 마이그레이션 시 동등한 특수 케이스가 있는지 확인이 필요하다(추측: 윤년 또는 미래 날짜 경고).

### 4. Monthly 말일 계산 — INTEGER-OF-DATE 패턴

```cobol
COMPUTE WS-CURDATE-N = FUNCTION DATE-OF-INTEGER(
        FUNCTION INTEGER-OF-DATE(WS-CURDATE-N) - 1)
```

다음 달 1일을 정수 일수로 환산 후 -1하여 이번 달 말일을 구하는 패턴이다. Java 등가:

```java
LocalDate endDate = YearMonth.of(year, month).atEndOfMonth();
```

### 5. GO TO RETURN-TO-CICS — 조기 탈출 패턴

`SEND-TRNRPT-SCREEN` 단락 끝의 `GO TO RETURN-TO-CICS`(라인 580)는 화면 전송 직후 반드시 RETURN하도록 강제하는 일종의 탈출 패턴이다. 현대화 시에는 예외 기반 흐름 제어 또는 메서드의 명시적 `return`으로 대체해야 한다.

```java
private void sendScreen(ScreenModel model) {
    renderScreen(model);
    throw new CicsReturnSignal(); // 또는 단순 return
}
```

### 6. pseudo-conversational 패턴 — Spring MVC Controller 매핑

| CICS 패턴 | Java/Spring 등가 |
|-----------|-----------------|
| `EIBCALEN = 0` | 세션 없음, 로그인 리다이렉트 |
| `CDEMO-PGM-REENTER = FALSE` | 첫 GET 요청 (빈 폼 렌더링) |
| `CDEMO-PGM-REENTER = TRUE` | POST 요청 (폼 처리) |
| `EXEC CICS RETURN TRANSID COMMAREA` | HTTP 응답 반환 + 세션 상태 저장 |
| `EXEC CICS XCTL` | HTTP 리다이렉트 |

### 7. 인라인 JCL 파라미터 주입 취약점

`PARM-START-DATE-1`/`PARM-END-DATE-1`은 화면 입력값을 JCL 텍스트에 직접 삽입한다. COBOL에서는 고정 길이 PIC X(10) 필드이므로 오버플로우가 없으나, 현대화 시 인젝션 공격을 방지하기 위해 엄격한 입력 검증과 파라미터 바인딩이 필요하다.

### 8. 데이터 타입 매핑 요약

| COBOL 필드 | PIC | Java 타입 | 비고 |
|-----------|-----|----------|------|
| `WS-RESP-CD` / `WS-REAS-CD` | `S9(09) COMP` | `int` | CICS 응답 코드, 2진수 |
| `WS-REC-COUNT` / `WS-IDX` | `S9(04) COMP` | `short` / `int` | 2진수 카운터 |
| 날짜 구성 필드들 | `PIC X(2/4)` | `String` (포맷팅용) → `LocalDate` | 검증 후 변환 권장 |
| `MONTHLYI`, `YEARLYI`, `CUSTOMI` | `PIC X(1)` | `boolean` (또는 `enum ReportType`) | Level-88 없이 SPACES/LOW-VALUES 비교 |
| `CONFIRMI` | `PIC X(1)` | `boolean` | `'Y'`/`'N'` 매핑 |
| `JCL-RECORD` | `PIC X(80)` | `String` (80자 고정) | EBCDIC→ASCII 변환 불필요(이미 ASCII로 리터럴 정의) |
