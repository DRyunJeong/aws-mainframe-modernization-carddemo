# CBTRN02C — 일일 거래 입력(Posting) 배치

- **유형**: 배치 COBOL
- **한 줄 요약**: 일일 거래 파일(DALYTRAN)을 한 건씩 읽어 카드/계정 유효성을 검증한 뒤, 통과 건은 거래 카테고리 잔액·계정 잔액을 갱신하고 거래 마스터(TRANSACT)에 기록하며, 실패 건은 거절 파일(DALYREJS)에 사유와 함께 기록하는 야간 배치 프로그램이다.

## 기능 설명

이 프로그램은 CardDemo 야간 배치 시퀀스에서 거래를 실제 원장에 "반영(posting)"하는 핵심 단계다(JCL job: `POSTTRAN`). 헤더 주석에 명시되어 있듯 기능은 "Post the records from daily transaction file"이다(line 5).

처리 단위는 일일 거래 한 건(`DALYTRAN-RECORD`)이며, 각 건마다 다음 두 단계를 수행한다.

1. **검증(`1500-VALIDATE-TRAN`)**: 카드번호로 교차참조(XREF)를 조회해 계정을 찾고(line 380-392), 그 계정을 읽어 신용한도 초과 여부와 카드 만료일 경과 여부를 확인한다(line 393-422). 검증 실패 시 `WS-VALIDATION-FAIL-REASON`에 4자리 거절코드가 채워진다.
2. **반영(`2000-POST-TRANSACTION`)** 또는 **거절(`2500-WRITE-REJECT-REC`)**: 검증 통과(`WS-VALIDATION-FAIL-REASON = 0`)면 잔액 갱신 + 거래 기록을, 실패면 원본 거래 + 검증 트레일러를 거절 파일에 기록한다(line 211-216).

검증은 부분적으로만 구현되어 있고("ADD MORE VALIDATIONS HERE", line 377), 확장 여지를 남긴 데모 코드다. 거절이 한 건이라도 있으면 종료 시 `RETURN-CODE`를 4로 설정해 후속 JCL 스텝이 부분 실패를 인지하도록 한다(line 229-231).

이 프로그램은 CICS를 거치지 않는 순수 배치다. `FILE-CONTROL`의 `SELECT ... ASSIGN`으로 VSAM/순차 파일을 직접 열고, 모든 I/O 직후 2바이트 FILE STATUS를 검사하는 전형적인 배치 골격을 따른다.

## 입력 / 출력

- **입력**:
  - `DALYTRAN-FILE`(DD: `DALYTRAN`) — 일일 거래 입력. 순차 파일, 드라이빙(driving) 파일. 레코드는 `CVTRA06Y`의 `DALYTRAN-RECORD`(RECLN 350)로 매핑(line 29-32, 102).
  - `XREF-FILE`(DD: `XREFFILE`) — 카드↔계정↔고객 교차참조. KSDS, RANDOM, 키 `FD-XREF-CARD-NUM`. 카드번호로 계정ID를 얻는 조회용. 입력 전용(line 40-44, 275).
  - `ACCOUNT-FILE`(DD: `ACCTFILE`) — 계정 마스터. KSDS, RANDOM, `OPEN I-O`. 읽기(검증) + 갱신(잔액) 모두 수행(line 51-55, 311).
  - `TCATBAL-FILE`(DD: `TCATBALF`) — 거래 카테고리 잔액. KSDS, RANDOM, `OPEN I-O`. 읽기 + 신규생성/갱신(line 57-61, 329).
- **출력**:
  - `TRANSACT-FILE`(DD: `TRANFILE`) — 거래 마스터(원장). KSDS, RANDOM, `OPEN OUTPUT`, 키 `FD-TRANS-ID`. 반영된 거래를 `WRITE`로 추가(line 34-38, 256, 564).
  - `DALYREJS-FILE`(DD: `DALYREJS`) — 거절 거래. 순차, `OPEN OUTPUT`. 원본 거래 350바이트 + 검증 트레일러 80바이트를 기록(line 46-49, 293, 451).
  - 표준출력(`DISPLAY`) — 처리/거절 건수 요약과 오류 메시지(line 227-232).
  - `RETURN-CODE` — 거절 발생 시 4(line 229-231).

> 주의: `ACCOUNT-FILE`과 `TCATBAL-FILE`은 입력이자 출력(`OPEN I-O`)이다.

## 의존성

- **COPY (카피북)**:
  - `CVTRA06Y` → `DALYTRAN-RECORD` (입력 거래 레이아웃, line 102)
  - `CVTRA05Y` → `TRAN-RECORD` (출력 거래 마스터 레이아웃, line 107)
  - `CVACT03Y` → `CARD-XREF-RECORD` (교차참조, `XREF-ACCT-ID` 제공, line 112)
  - `CVACT01Y` → `ACCOUNT-RECORD` (계정 마스터, line 121)
  - `CVTRA01Y` → `TRAN-CAT-BAL-RECORD` (거래 카테고리 잔액, line 126)
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CALL 'CEE3ABD' USING ABCODE, TIMING` — LE(Language Environment) 강제 abend 서비스. 복구 불가 I/O 오류 시 ABEND 코드 999로 프로그램을 비정상 종료(line 707-711).
  - 그 외 애플리케이션 서브프로그램 호출 없음(`XCTL`/`LINK`는 CICS 전용이라 배치인 이 프로그램에는 해당 없음).
- **데이터셋/파일/DB 테이블**:
  - VSAM/순차 6개: `DALYTRAN`(QSAM 순차 입력), `TRANFILE`(KSDS), `XREFFILE`(KSDS), `DALYREJS`(QSAM 순차 출력), `ACCTFILE`(KSDS), `TCATBALF`(KSDS). DB2/SQL 사용 없음.
  - DB2 타임스탬프는 *값*만 흉내 낸다: `Z-GET-DB2-FORMAT-TIMESTAMP`가 `FUNCTION CURRENT-DATE`를 `EEEE-MM-DD-UU.MM.SS.HH0000` 포맷 문자열로 조립할 뿐, DB2 연결은 없다(line 692-705).
- **트랜잭션 ID 또는 EXEC PGM**:
  - 트랜잭션 ID 없음(배치). `EXEC PGM=CBTRN02C` (JCL job `POSTTRAN`에서 기동, app/jcl/POSTTRAN.jcl). PARM 수신 없음 — `PROCEDURE DIVISION`에 `USING` 절이 없다(line 193).

## 핵심 로직 흐름

전체 골격은 `PROCEDURE DIVISION`의 메인 루프(line 193-234)다.

1. **OPEN 단계** (line 195-200): 6개 파일을 각각 `0000`~`0500` 단락으로 연다. 각 OPEN 단락은 FILE STATUS가 `'00'`이 아니면 `9999-ABEND-PROGRAM`으로 즉시 abend한다.

2. **메인 처리 루프** (line 202-219): `PERFORM UNTIL END-OF-FILE = 'Y'`.
   - `1000-DALYTRAN-GET-NEXT`로 다음 거래를 읽는다(`READ ... INTO`). FILE STATUS `'00'`=정상, `'10'`=EOF(→`END-OF-FILE='Y'`), 그 외=abend(line 345-369).
   - 정상 읽기면: `WS-TRANSACTION-COUNT` 증가, **검증 트레일러를 매 건 초기화**(`MOVE 0 TO WS-VALIDATION-FAIL-REASON`, `MOVE SPACES ...`, line 208-209) — 이전 건의 거절 사유가 누수되지 않게 하는 중요한 리셋이다.
   - `1500-VALIDATE-TRAN` 수행 후 분기:
     - `WS-VALIDATION-FAIL-REASON = 0` → `2000-POST-TRANSACTION`
     - 아니면 → `WS-REJECT-COUNT` 증가 + `2500-WRITE-REJECT-REC` (line 211-216).

3. **CLOSE 단계 + 요약** (line 221-234): 6개 파일을 닫고, 처리/거절 건수를 `DISPLAY`, 거절>0이면 `RETURN-CODE=4`, `GOBACK`.

### 검증 흐름 (`1500-VALIDATE-TRAN`, line 370-422) — 가장 주의할 부분

`1500-VALIDATE-TRAN`은 두 하위 단락을 순차 호출하되, XREF 조회가 실패하면 계정 조회를 건너뛴다(line 372-376).

- **`1500-A-LOOKUP-XREF`** (line 380-392): `DALYTRAN-CARD-NUM`을 `FD-XREF-CARD-NUM`에 옮겨 XREF를 RANDOM READ. `INVALID KEY`(키 없음)면 거절코드 **100**("INVALID CARD NUMBER FOUND")을 설정한다. 정상이면 `CARD-XREF-RECORD`에 `XREF-ACCT-ID`가 채워진다.

- **`1500-B-LOOKUP-ACCT`** (line 393-422): `XREF-ACCT-ID`를 `FD-ACCT-ID`에 옮겨 계정을 RANDOM READ. `INVALID KEY`면 거절코드 **101**("ACCOUNT RECORD NOT FOUND"). 정상이면 두 가지 검사를 **연속(short-circuit 아님)** 수행한다:
  - **신용한도 검사** (line 403-413): `WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT`를 계산하고, `ACCT-CREDIT-LIMIT >= WS-TEMP-BAL`이 아니면 거절코드 **102**("OVERLIMIT TRANSACTION"). ※ 현재잔액(`ACCT-CURR-BAL`)이 아니라 **사이클 크레딧−사이클 데빗+거래액**을 한도와 비교한다는 점에 유의.
  - **만료일 검사** (line 414-420): `ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS (1:10)`이 아니면 거절코드 **103**("TRANSACTION RECEIVED AFTER ACCT EXPIRATION"). 두 날짜 모두 `X(10)` 형태(`YYYY-MM-DD`)라 *문자열 비교*로 날짜 대소를 판단한다(참조 인용 기준 필드: copybook `ACCT-EXPIRAION-DATE` `PIC X(10)`, `DALYTRAN-ORIG-TS` `PIC X(26)`의 앞 10자).
  - 두 검사는 `END-IF`로 독립 실행되므로, 102가 설정된 뒤 만료까지 걸리면 **103이 102를 덮어쓴다**(마지막 사유만 남음). (사유 코드가 하나만 보존된다는 점은 Java 이식 시 명시적 정책이 필요하다.)

### 반영 흐름 (`2000-POST-TRANSACTION`, line 424-444)

1. `DALYTRAN-*` 필드를 `TRAN-*`(출력 거래 레코드)로 1:1 복사(line 425-436). `TRAN-PROC-TS`에는 `Z-GET-DB2-FORMAT-TIMESTAMP`가 만든 현재 타임스탬프를 채운다(line 437-438).
2. `2700-UPDATE-TCATBAL` → `2800-UPDATE-ACCOUNT-REC` → `2900-WRITE-TRANSACTION-FILE` 순서로 수행(line 440-442).

- **`2700-UPDATE-TCATBAL`** (line 467-501): 키(계정ID+타입+카테고리)로 TCATBAL을 READ.
  - `INVALID KEY`면 `WS-CREATE-TRANCAT-REC='Y'`로 표시하고 안내 메시지 출력(line 475-479).
  - 상태 검사에서 `'00' OR '23'`(정상 또는 키없음)을 정상으로 간주, 그 외만 abend(line 481-493).
  - 플래그에 따라 분기: 신규면 `2700-A-CREATE-TCATBAL-REC`(레코드 INITIALIZE 후 키 세팅, `TRAN-CAT-BAL = DALYTRAN-AMT`, `WRITE`, line 503-524), 기존이면 `2700-B-UPDATE-TCATBAL-REC`(`ADD DALYTRAN-AMT TO TRAN-CAT-BAL` 후 `REWRITE`, line 526-542).

- **`2800-UPDATE-ACCOUNT-REC`** (line 545-560): `ACCT-CURR-BAL`에 거래액을 더하고(line 547), 부호로 분기하여 양수면 `ACCT-CURR-CYC-CREDIT`에, 음수면 `ACCT-CURR-CYC-DEBIT`에 누적(line 548-552) 후 `REWRITE`. `INVALID KEY`면 거절코드 109를 설정하지만 이미 검증을 통과해 읽어둔 레코드라 정상 경로에서는 거의 발생하지 않는다(line 555-558).

- **`2900-WRITE-TRANSACTION-FILE`** (line 562-579): 완성된 `TRAN-RECORD`를 거래 마스터에 `WRITE`. 실패 시 abend.

### 거절 흐름 (`2500-WRITE-REJECT-REC`, line 446-465)

원본 `DALYTRAN-RECORD`(350)를 `REJECT-TRAN-DATA`에, `WS-VALIDATION-TRAILER`(거절코드 4자리 + 설명 76자)를 `VALIDATION-TRAILER`에 옮겨 한 레코드(430바이트)로 `WRITE`한다.

### 공통 오류 처리

- **`9910-DISPLAY-IO-STATUS`** (line 714-727): FILE STATUS를 사람이 읽을 수 있는 형태로 출력. 상태가 비숫자이거나 첫 바이트가 `'9'`면(VSAM 확장 상태) 두 번째 바이트를 바이너리로 재해석한다 — `TWO-BYTES-BINARY`/`TWO-BYTES-ALPHA` `REDEFINES`를 이용한 바이트 재해석 트릭(line 134-137).
- **`9999-ABEND-PROGRAM`** (line 707-711): `CALL 'CEE3ABD'`로 ABEND 코드 999 강제 종료.

## Java/현대화 노트

- **전체 구조 → Spring Batch**: `DALYTRAN`을 `ItemReader`(FlatFileItemReader), 건별 검증+반영을 `ItemProcessor`, 거래 마스터 기록을 `ItemWriter`로 매핑할 수 있다. 단, 이 프로그램은 한 건 처리 중에 **여러 파일을 동시에 갱신**(TCATBAL/ACCOUNT 두 KSDS + TRANSACT WRITE)하므로, 단순 read→process→write 청크보다는 프로세서 안에서 리포지토리 3개를 다루는 형태가 자연스럽다. 거절은 별도 출력(skip/`SkipListener` 또는 두 번째 writer)으로 분리.

- **금액·잔액은 반드시 `BigDecimal`**: `DALYTRAN-AMT`/`TRAN-CAT-BAL`은 `PIC S9(9)V99`(부호+정수9+소수2), `ACCT-CURR-BAL`/`ACCT-CREDIT-LIMIT`/`ACCT-CURR-CYC-CREDIT`/`ACCT-CURR-CYC-DEBIT`는 `PIC S9(10)V99`다. `V`는 *implied decimal*(저장 위치에 소수점 문자가 없음)이므로 → Java `BigDecimal scale=2`. `double`/`float`는 금전 계산에서 절대 금지(반올림 오차). 한도 비교(line 407)도 `BigDecimal.compareTo`로.

- **신용한도 검사 로직 그대로 옮기기**: 한도 비교 대상이 `ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + amount`라는 점을 Java에서도 동일하게 재현해야 한다(line 403-405). 직관적으로 `현재잔액 + 거래액 <= 한도`가 아니므로, 비즈니스 의미를 임의로 "정정"하지 말 것.

- **검증 사유 단일 보존 vs 누적**: 현재 COBOL은 사유 코드 하나만 남기고 뒤 검사가 앞 검사를 덮을 수 있다(102→103, line 407-420). Java로 옮길 때 `List<RejectReason>`로 *모든* 위반을 모을지, 레거시 동작(마지막 한 건)을 그대로 보존할지 **명시적으로 결정**해야 한다. 리그레션 테스트를 위해서는 레거시 동작 보존이 안전하다.

- **날짜 비교는 문자열 비교임**: 만료일 검사(line 414)는 `YYYY-MM-DD` 형식 두 문자열의 사전식 비교다. 형식이 고정(`X(10)`)이라 우연히 날짜 대소와 일치한다. Java에서는 `LocalDate.parse(...).isBefore(...)` 같은 타입 안전 비교로 바꾸되, 입력 데이터에 비표준 형식이나 공백이 섞이면 동작이 달라질 수 있음을 주의(레거시는 파싱 없이 비교하므로 예외가 안 났을 수 있다).

- **`INVALID KEY` → 조회 결과 없음**: VSAM RANDOM READ의 `INVALID KEY`는 "키에 해당하는 레코드 없음"이다(line 384, 396, 475). Java로는 `Optional.isEmpty()` 또는 `findById(...) == null` 분기로 매핑. TCATBAL의 "없으면 생성"(upsert) 패턴(line 495-499)은 JPA `save()`/`merge()` 또는 `INSERT ... ON CONFLICT`로.

- **트랜잭션/원자성 경계가 없음**: 이 배치는 명시적 commit/rollback이나 SYNCPOINT가 없다. TCATBAL 갱신 후 ACCOUNT 갱신 또는 TRANSACT WRITE에서 abend하면 **부분 갱신 상태**가 남을 수 있다(VSAM은 자동 롤백 없음). Java 이식 시 한 건 처리를 `@Transactional` 단위로 묶으면 레거시보다 *더 안전*해지지만, 재처리(idempotency)·재시작 전략은 새로 설계해야 한다.

- **고정 길이 레코드 + FILLER**: 모든 레코드는 고정 길이이며 끝에 `FILLER`로 패딩(예: DALYTRAN 끝 `FILLER X(20)`, line 18 of CVTRA06Y). `MOVE`는 잘림/공백채움 규칙을 따른다. Java DTO는 길이 검증과 trailing-space trim 정책을 명시할 것.

- **`REDEFINES`(바이트 재해석)에 직접 대응 없음**: `9910-DISPLAY-IO-STATUS`의 `TWO-BYTES-BINARY`/`TWO-BYTES-ALPHA REDEFINES`(line 134-137)는 같은 2바이트 메모리를 바이너리/문자 두 관점으로 보는 union이다. Java에는 직접 등가물이 없으므로 `ByteBuffer`나 비트 연산으로 풀어야 한다 — 다만 이는 진단용 로깅이라 현대 환경에선 단순 로깅으로 대체 가능.

- **`CALL 'CEE3ABD'` → 예외 전파**: 복구 불가 I/O 오류 시 강제 abend(line 707-711). Java에서는 unchecked 예외를 던져 배치 잡을 실패시키고, 잡 스케줄러가 비정상 종료를 인지하게 하는 것이 등가. `RETURN-CODE=4`(부분 실패, line 230)는 잡 종료 코드/`ExitStatus`로 매핑.

- **타임스탬프 생성**: `Z-GET-DB2-FORMAT-TIMESTAMP`(line 692-705)는 `FUNCTION CURRENT-DATE`를 DB2 타임스탬프 문자열로 *수동 포맷팅*한다. Java에서는 `LocalDateTime.now()` + `DateTimeFormatter`(패턴 `yyyy-MM-dd-HH.mm.ss.SSSSSS`)로 한 줄 대체. 끝 `0000` 패딩(밀리초 자리)은 정밀도 차이이므로 포맷 패턴에 반영 필요.

- **(추측) 미구현 검증 확장점**: line 377의 "ADD MORE VALIDATIONS HERE" 주석으로 보아, 가맹점/거래유형 유효성 등 추가 검증이 의도되었으나 데모에선 생략된 것으로 보인다. 실제 모더나이제이션 시 누락 규칙을 업무 요건에서 보강해야 할 수 있다.
