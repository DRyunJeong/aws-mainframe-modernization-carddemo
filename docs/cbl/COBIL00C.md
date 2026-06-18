# COBIL00C — 청구서 결제 (Bill Payment) 온라인 화면

- **유형**: CICS 온라인 COBOL
- **한 줄 요약**: 계정 ID를 입력받아 현재 잔액 전액을 청구서 결제로 처리하고, 결제 거래(TRANSACT) 1건을 생성한 뒤 계정 잔액을 0으로 차감하는 CICS 트랜잭션 화면(TRANID `CB00`).

## 기능 설명

이 프로그램은 CardDemo의 청구서 "전액 결제" 기능을 담당하는 BMS 화면 프로그램이다. 헤더 주석(L5-6)에 "Bill Payment - Pay account balance in full and a tr...saction for the online bill payment"라고 명시되어 있다.

사용자가 화면(`COBIL0A`)에서 계정 ID(`ACTIDINI`)를 입력하고 ENTER를 누르면:
1. 계정(`ACCTDAT`)을 읽어 현재 잔액(`ACCT-CURR-BAL`)을 화면에 표시한다.
2. 확인 필드(`CONFIRMI`)에 `Y`를 입력하면, 잔액 전액에 대한 결제 거래를 `TRANSACT` 파일에 새로 기록하고, 계정 잔액에서 그 금액을 차감(결과적으로 0)한 뒤 계정을 갱신한다.

`CONFIRM`이 비어 있으면 잔액만 조회·표시하고 "Confirm to make a bill payment..." 안내를 띄우며(L237-239), `N`이면 화면을 초기화한다(L178-181). 잔액이 0 이하이면 "You have nothing to pay..."로 결제를 막는다(L197-205).

전체 골격은 CardDemo의 표준 pseudo-conversational 패턴을 따른다(WS-PGMNAME/WS-TRANID 보유, `EIBCALEN`/`CDEMO-PGM-REENTER` 분기, 매 턴 `EXEC CICS RETURN ... COMMAREA` 종료 — L146-149). 이 공통 구조는 에이전트 메모리 `online_pseudoconv_pattern.md`에 기록된 내용과 일치한다.

## 입력 / 출력

- **입력**:
  - BMS 화면 입력 맵 `COBIL0AI`(MAPSET `COBIL00`, `EXEC CICS RECEIVE MAP('COBIL0A')` — L308-314). 주요 입력 필드: 계정 ID `ACTIDINI`(PIC X(11)), 확인 여부 `CONFIRMI`(PIC X(1)).
  - 키 입력 `EIBAID`: ENTER / PF3(이전 화면) / PF4(화면 초기화) (L125-142).
  - 진입 시 commarea `CARDDEMO-COMMAREA`(이전 프로그램에서 전달된 세션 상태). 특히 `CDEMO-CB00-TRN-SELECTED`가 채워져 있으면 그 값을 계정 ID 입력으로 미리 세팅하고 ENTER 처리를 선실행한다(L116-121) — 즉, 다른 화면(거래 조회 등)에서 선택한 계정으로 진입하는 경로.
  - VSAM 읽기: 계정 레코드(`ACCTDAT`), 카드 교차참조(`CXACAIX`), 마지막 거래번호 채번을 위한 거래 파일 역방향 브라우즈(`TRANSACT`).
- **출력**:
  - BMS 화면 출력 맵 `COBIL0AO`(`EXEC CICS SEND MAP('COBIL0A')` — L295-301). 현재 잔액 `CURBALI`(L194), 에러/성공 메시지 `ERRMSGO`(L293), 성공 시 초록색(`DFHGREEN`)으로 "Payment successful. Your Transaction ID is ..."(L526-532).
  - VSAM 쓰기: `TRANSACT`에 신규 결제 거래 1건 `WRITE`(L512-520), `ACCTDAT` 계정 잔액 `REWRITE`(L379-385).

## 의존성

- **COPY (카피북)**:
  - `COCOM01Y` — `CARDDEMO-COMMAREA`(프로그램 간 세션 상태). 본 프로그램은 그 끝에 `CDEMO-CB00-INFO`(거래 ID 페이지/선택 정보) 서브구조를 인라인으로 확장(L63-72).
  - `COBIL00` — BMS 심볼릭 맵(`COBIL0AI`/`COBIL0AO`). 위치는 `app/cpy-bms/COBIL00.CPY`(L74).
  - `COTTL01Y` — 화면 제목 상수(`CCDA-TITLE01/02`, L323-324).
  - `CSDAT01Y` — 현재 날짜/시간 작업영역(`WS-CURDATE-DATA` 등, L321-338).
  - `CSMSG01Y` — 공통 메시지 상수(`CCDA-MSG-INVALID-KEY`, L140).
  - `CVACT01Y` — `ACCOUNT-RECORD`(계정 레코드, ACCT-ID/ACCT-CURR-BAL).
  - `CVACT03Y` — `CARD-XREF-RECORD`(카드/계정/고객 교차참조, XREF-ACCT-ID/XREF-CARD-NUM).
  - `CVTRA05Y` — `TRAN-RECORD`(거래 레코드, RECLN 350).
  - `DFHAID`(EIBAID 키 상수 DFHENTER/DFHPF3/DFHPF4), `DFHBMSCA`(BMS 색상 속성 DFHGREEN 등).
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `EXEC CICS XCTL`로 이동(RETURN-TO-PREV-SCREEN, L281-284). 대상은 `CDEMO-TO-PROGRAM`이며:
    - 최초 진입(`EIBCALEN = 0`) 시 `COSGN00C`(로그인) (L108).
    - PF3 종료 시 `CDEMO-FROM-PROGRAM`(호출한 화면)으로 복귀, 비어 있으면 `COMEN01C`(메인 메뉴) (L128-135).
  - 다른 프로그램을 CALL/LINK 하지는 않음(독립 화면 프로그램).
- **데이터셋/파일/DB 테이블**:
  - `TRANSACT`(WS-TRANSACT-FILE, L40) — 거래 KSDS. STARTBR/READPREV/ENDBR로 마지막 TRAN-ID를 읽어 +1 채번(L213-217), 신규 거래 WRITE(L512).
  - `ACCTDAT`(WS-ACCTDAT-FILE, L41) — 계정 VSAM. READ ... UPDATE(L345-354) 후 REWRITE(L379-385).
  - `CXACAIX`(WS-CXACAIX-FILE, L42) — 카드 교차참조의 계정 ID 대체 인덱스(AIX). 계정 ID로 읽어 `XREF-CARD-NUM`을 얻어 거래 레코드에 채움(L410-417, L225).
  - DB2/SQL 사용 없음.
- **트랜잭션 ID 또는 EXEC PGM**:
  - 트랜잭션 ID `CB00`(WS-TRANID, L38). 매 턴 `EXEC CICS RETURN TRANSID('CB00')`로 자기 자신을 다음 입력에 재기동(L146-149).
  - 프로그램명 `COBIL00C`(WS-PGMNAME, L37).

## 핵심 로직 흐름

1. **MAIN-PARA 진입 분기**(L99-149)
   - 에러/수정 플래그 초기화(L101-102), 메시지 영역 클리어(L104-105).
   - `EIBCALEN = 0`(commarea 없이 직접 진입) → `COSGN00C`로 XCTL(L107-109).
   - commarea 존재 시 `CARDDEMO-COMMAREA`로 복사(L111).
     - **첫 그리기**(`NOT CDEMO-PGM-REENTER`, L112-122): REENTER 플래그 set, 화면 클리어, 커서 위치 `-1`. `CDEMO-CB00-TRN-SELECTED`가 채워져 있으면 그 계정 ID로 `PROCESS-ENTER-KEY` 선실행. 그 후 `SEND-BILLPAY-SCREEN`.
     - **응답 처리**(REENTER, L123-142): `RECEIVE-BILLPAY-SCREEN` 후 `EVALUATE EIBAID`:
       - `DFHENTER` → `PROCESS-ENTER-KEY`.
       - `DFHPF3` → 이전 프로그램/메뉴로 복귀(L128-135).
       - `DFHPF4` → `CLEAR-CURRENT-SCREEN`(화면 초기화, L136-137).
       - 그 외 키 → "invalid key" 에러 후 재전송(L138-141).
   - 매 턴 `EXEC CICS RETURN TRANSID('CB00') COMMAREA`(L146-149).

2. **PROCESS-ENTER-KEY**(L154-244) — 핵심 비즈니스 로직
   - `CONF-PAY-NO` 초기화(L156).
   - 계정 ID 공백 검증: 비어 있으면 "Acct ID can NOT be empty..."(L158-167).
   - 계정 ID를 `ACCT-ID`와 `XREF-ACCT-ID`에 동시 세팅(L170-171).
   - `CONFIRMI` 값에 따라 분기(L173-191):
     - `Y`/`y` → `CONF-PAY-YES` set + `READ-ACCTDAT-FILE`.
     - `N`/`n` → 화면 초기화 + 에러 플래그(결제 중단).
     - 공백/LOW-VALUES → `READ-ACCTDAT-FILE`(잔액 조회만).
     - 그 외 → "Invalid value. Valid values are (Y/N)..." 에러.
   - 읽은 `ACCT-CURR-BAL`을 편집 후 화면 `CURBALI`에 표시(L193-194).
   - 잔액 ≤ 0 검증: "You have nothing to pay..."(L197-206).
   - **결제 실행 블록**(L208-244), `CONF-PAY-YES`일 때만:
     1. `READ-CXACAIX-FILE`로 카드번호 조회(L211).
     2. `TRAN-ID`에 HIGH-VALUES → STARTBR → READPREV → ENDBR로 마지막 거래번호를 얻어 +1 채번(L212-217). (ENDFILE면 0에서 시작 → 결과 1, L487-488.)
     3. `INITIALIZE TRAN-RECORD` 후 결제 거래 필드 세팅: TYPE `'02'`, CAT `2`, SOURCE `'POS TERM'`, DESC `'BILL PAYMENT - ONLINE'`, 금액 = 현재 잔액, 카드번호 = `XREF-CARD-NUM`, MERCHANT-ID `999999999`, MERCHANT-NAME `'BILL PAYMENT'`(L218-229).
     4. `GET-CURRENT-TIMESTAMP`로 `TRAN-ORIG-TS`/`TRAN-PROC-TS` 채움(L230-232).
     5. `WRITE-TRANSACT-FILE`로 거래 기록(L233).
     6. `COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT`(전액 결제이므로 0) 후 `UPDATE-ACCTDAT-FILE`로 REWRITE(L234-235).
     - 미확인이면 "Confirm to make a bill payment..." 안내(L236-240).
   - 마지막에 `SEND-BILLPAY-SCREEN`(L242).

3. **타임스탬프 생성**(GET-CURRENT-TIMESTAMP, L249-267): `EXEC CICS ASKTIME` + `FORMATTIME`으로 `YYYY-MM-DD HH:MM:SS` 형식 문자열을 만들어 `WS-TIMESTAMP`(26자, `CVTRA05Y` 거래 TS 형식)에 조립. 밀리초 6자리는 0(L266).

4. **에러 처리 공통 패턴**: 모든 VSAM 동사 후 `EVALUATE WS-RESP-CD`로 `DFHRESP(NORMAL)`/`NOTFND`/`OTHER` 분기, 실패 시 에러 플래그 + 메시지 + 커서 세팅 후 `SEND-BILLPAY-SCREEN`(예: READ-ACCTDAT-FILE L356-372).

5. **WRITE 성공 시 결과 메시지**(WRITE-TRANSACT-FILE, L522-547): NORMAL이면 전 필드 초기화 + 초록색 + `STRING`으로 "Payment successful. Your Transaction ID is <TRAN-ID>." 조립. DUPKEY/DUPREC면 "Tran ID already exist...".

## Java/현대화 노트

- **트랜잭션 경계와 데이터 정합성**: 거래 WRITE → 계정 REWRITE가 한 CICS 트랜잭션(`CB00`) 안에서 일어난다. 이 둘은 같은 UOW(Unit of Work)이므로 RETURN 시점까지 묶여 commit/rollback 된다. Java로 옮길 때는 두 쓰기(거래 insert + 계정 update)를 반드시 하나의 `@Transactional`(JTA/Spring) 경계로 묶어야 한다. **(주의)** 현재 코드는 `READ ... UPDATE`(L351)로 계정 레코드를 잠그지만, `TRANSACT` 채번(STARTBR/READPREV)에는 락이 없어 동시 결제 시 같은 TRAN-ID를 채번할 위험이 있다(채번 경합). Java에서는 거래 ID를 DB 시퀀스/IDENTITY로 대체하는 것이 안전하다.

- **채번 로직(STARTBR/READPREV/ENDBR)**: "마지막 키를 역방향으로 1건 읽어 +1" 패턴(L212-217)은 VSAM 커서 브라우징 관용구다. Java/RDB에서는 `SELECT MAX(id)` 후 +1이 아니라, 데이터베이스 시퀀스나 auto-increment PK로 치환하는 것이 권장된다(경합/성능 모두 개선).

- **금액·정밀도(packed/edited)**:
  - `TRAN-AMT`는 `PIC S9(09)V99`(`CVTRA05Y`), 즉 정수부 9자리 + 소수 2자리의 고정 소수점 금액 → Java `BigDecimal`(scale 2)로 매핑. float/double 금지(정밀도 손실).
  - `ACCT-CURR-BAL`도 동일하게 `BigDecimal`. `COMPUTE ... - TRAN-AMT`(L234)는 `balance.subtract(amount)`.
  - WS-TRAN-AMT(`PIC +99999999.99`), WS-CURR-BAL(`PIC +9999999999.99`)는 **편집(display) 포맷**으로, 화면 표시용 문자열이다(부호+자리수+소수점 리터럴). Java에서는 도메인 값(BigDecimal)과 표시 문자열(`DecimalFormat`/`String.format`)을 분리하라.

- **고정 길이 / 1-기준 / null 부재**:
  - 화면 필드는 고정 길이(`ACTIDINI` X(11), `CONFIRMI` X(1)). 빈 값은 `SPACES`/`LOW-VALUES`로 표현되며 Java의 `null`/`""` 와 의미가 다르다. L116-117, L159, L182-183처럼 "SPACES OR LOW-VALUES"를 빈값으로 취급하는 검사는 Java에서 `isBlank()` + null 체크로 옮기되, 화면 미입력(LOW-VALUES)과 사용자가 지운 공백(SPACES)을 동일 취급한다는 점을 유지.
  - `WS-TIMESTAMP(01:10)` 같은 부분참조(reference modification, L264-265)는 1-기준 오프셋·길이. Java `substring`은 0-기준이므로 변환 시 인덱스 보정 필요.

- **BMS 심볼릭 맵 → DTO**: `COBIL0AI`/`COBIL0AO`는 같은 메모리를 REDEFINES로 겹친 입력/출력 뷰다(`COBIL0AO REDEFINES COBIL0AI`, cpy-bms L79). 각 필드의 접미사 `L`(길이/커서), `F`(플래그), `I`(입력값) / `O`(출력값), `C`(색상) 컨벤션은 메모리 `copybook_record_layouts.md`에 정리됨. Java/웹에서는 화면 요청 DTO와 응답 DTO로 분리하고, `MOVE -1 TO ...L`(커서 강제 위치, 예 L163)은 UI 포커스 처리로 대체한다.

- **pseudo-conversational → 무상태 웹**: 매 키 입력마다 트랜잭션이 새로 뜨고 `CARDDEMO-COMMAREA`로 상태를 복원하는 구조는 HTTP 무상태 요청 + 세션/토큰과 동형이다. `CDEMO-PGM-REENTER`(첫 그리기 vs 응답 처리) 분기는 GET(화면 표시)/POST(폼 제출) 분리에 대응. PF3=뒤로가기, PF4=폼 리셋, ENTER=submit으로 매핑 가능.

- **GOBACK/STOP RUN 없음**: 종료는 항상 `EXEC CICS RETURN`(같은 트랜잭션 재기동) 또는 `XCTL`(다른 프로그램으로 제어 이양, 돌아오지 않음 = HTTP redirect). Java에서 `XCTL`은 다른 컨트롤러로의 forward/redirect, `RETURN TRANSID`는 같은 화면 컨트롤러의 다음 요청 대기로 본다.

- **단락 fall-through 주의**: 이 프로그램은 모든 처리 단락을 `PERFORM`으로만 호출하고 `GO TO`가 없어, 명시적 폴스루 의존은 없다. 다만 `PERFORM SEND-BILLPAY-SCREEN` 후에도 호출자(PROCESS-ENTER-KEY 등)로 복귀해 계속 진행하므로, 에러 분기에서 화면을 보낸 뒤에도 `IF NOT ERR-FLG-ON` 가드(L169, L197, L208)로 후속 로직을 건너뛰는 흐름을 Java에서는 early-return으로 표현하는 편이 명확하다.
