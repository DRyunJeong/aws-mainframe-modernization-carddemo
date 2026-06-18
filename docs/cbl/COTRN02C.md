# COTRN02C — 거래 추가 화면 (Add Transaction)

- **유형**: CICS 온라인 COBOL
- **한 줄 요약**: 계정/카드 번호를 검증한 뒤 운영자가 입력한 거래 데이터를 받아 `TRANSACT` VSAM 파일에 새 거래 레코드 1건을 추가하는 CICS 온라인 화면 프로그램. (소스 헤더 5행 `Function: Add a new Transaction to TRANSACT file`)

---

## 기능 설명

이 프로그램은 CardDemo의 **거래 등록(Add Transaction)** 화면(BMS 맵 `COTRN2A`)을 담당한다. 트랜잭션 ID는 `CT02`(36행 `WS-TRANID VALUE 'CT02'`)이다.

운영자는 화면에서 **계정 ID(Account ID)** 또는 **카드 번호(Card Number)** 중 하나를 입력하고, 거래 상세(유형코드, 카테고리코드, 출처, 설명, 금액, 발생/처리일자, 가맹점 정보)를 채운 뒤 확인(`Confirm = Y`)하면 새 거래가 저장된다.

핵심 동작은 다음과 같다.
- 계정 ID 입력 시 `CXACAIX`(계정ID 보조 인덱스)로 카드번호를 역조회, 반대로 카드번호 입력 시 `CCXREF`로 계정ID를 역조회한다 (208, 222행).
- 모든 입력 필드를 비어있음/숫자형식/날짜형식 관점에서 검증하고, 날짜는 외부 유틸리티 `CSUTLDTC`를 호출해 실제 유효 날짜인지 확인한다 (393, 413행).
- 신규 거래 ID는 자동 채번한다. `TRANSACT` 파일을 키 역방향으로 브라우즈해 **마지막(최댓값) TRAN-ID + 1**을 새 ID로 사용한다 (444~449행).
- 기능키: `Enter`=처리·저장, `PF3`=이전 화면 복귀, `PF4`=화면 초기화, `PF5`=직전 거래 데이터 복사(템플릿처럼 재사용) (133~152행).

Java로 보면 **하나의 "Add Transaction" 요청 핸들러(Controller) + 입력 검증기(Validator) + 거래 Repository**를 한 파일에 합쳐놓은 형태다. 메뉴 화면(COMEN01C 등)에서 특정 거래를 선택해 넘어오는 경로도 지원한다 (124~129행: commarea의 `CDEMO-CT02-TRN-SELECTED`가 채워져 있으면 그 값을 카드번호로 보고 자동 조회).

---

## 입력 / 출력

- **입력**:
  - BMS 화면 입력(`COTRN2AI`): 계정ID(`ACTIDINI`), 카드번호(`CARDNINI`), 거래유형코드(`TTYPCDI`), 카테고리코드(`TCATCDI`), 출처(`TRNSRCI`), 금액(`TRNAMTI`), 설명(`TDESCI`), 발생일자(`TORIGDTI`), 처리일자(`TPROCDTI`), 가맹점ID/이름/도시/우편번호(`MIDI/MNAMEI/MCITYI/MZIPI`), 확인(`CONFIRMI`).
  - CICS COMMAREA(`CARDDEMO-COMMAREA`, COCOM01Y) — 이전 프로그램/사용자 컨텍스트 및 `CDEMO-CT02-INFO`(선택된 거래 정보) 전달 (71~80행).
  - `EIBAID`(눌린 키), `EIBCALEN`(commarea 길이).
- **출력**:
  - BMS 화면 출력(`COTRN2AO`) — 입력 에코, 헤더(제목/날짜/시간), 에러/성공 메시지(`ERRMSGO`). 성공 시 녹색 메시지 + 채번된 TRAN-ID 표시 (727~733행).
  - **`TRANSACT` VSAM KSDS에 신규 거래 레코드 1건 WRITE** (713행, 부수효과).

---

## 의존성

- **COPY (카피북)**:
  - `COCOM01Y` — `CARDDEMO-COMMAREA`(프로그램 간 세션 상태). 이 프로그램은 그 아래에 `CDEMO-CT02-INFO`(거래 선택 페이징 정보)를 인라인으로 확장 (71~80행).
  - `COTRN02` — BMS 심볼릭 맵(`COTRN2AI`/`COTRN2AO`), 화면 필드 정의 (82행).
  - `COTTL01Y` — 화면 타이틀 상수(`CCDA-TITLE01/02`).
  - `CSDAT01Y` — 현재 날짜/시간 작업영역(`WS-CURDATE-*`, `WS-CURTIME-*`).
  - `CSMSG01Y` — 공통 메시지 상수(`CCDA-MSG-INVALID-KEY` 등).
  - `CVTRA05Y` — `TRAN-RECORD`(거래 레코드, RECLN 350) 레이아웃 (88행).
  - `CVACT01Y` — `ACCOUNT-RECORD`(계정 레코드). *주의: COPY는 되어 있으나 PROCEDURE에서 직접 사용하는 곳은 확인되지 않음 (추측: 미사용/잔존 의존).*
  - `CVACT03Y` — `CARD-XREF-RECORD`(카드-계정-고객 교차참조, RECLN 50) (90행).
  - `DFHAID` — CICS AID 키 상수(`DFHENTER/DFHPF3/DFHPF4/DFHPF5`).
  - `DFHBMSCA` — BMS 화면 속성 상수(`DFHGREEN` 등).
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CALL 'CSUTLDTC'` — 날짜 검증 유틸리티(정적 CALL, 발생/처리일자 각각 호출) (393, 413행).
  - `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)` — 복귀 시 동적 화면 전환 (508~511행). 대상은 `EIBCALEN=0`이거나 미설정이면 `COSGN00C`(로그인), `PF3`이고 호출원이 비어있으면 `COMEN01C`(메뉴), 그 외에는 `CDEMO-FROM-PROGRAM`(호출해 온 프로그램) (116, 137~142, 502~503행).
- **데이터셋/파일/DB 테이블** (모두 VSAM, CICS `DATASET` 논리명):
  - `TRANSACT` (KSDS) — 거래 마스터. STARTBR/READPREV/ENDBR로 최대 키 조회 후 WRITE (444~466, 642~749행). DB2/SQL은 사용하지 않음.
  - `CXACAIX` — 계정ID 기준 카드 교차참조 보조 인덱스(AIX). 계정ID로 READ해 카드번호 획득 (576~604행).
  - `CCXREF` — 카드번호 기준 카드 교차참조. 카드번호로 READ해 계정ID 획득 (609~637행).
  - `ACCTDAT` — `WS-ACCTDAT-FILE`로 파일명 리터럴만 정의(40행)되어 있고 실제 접근 동사는 확인되지 않음 (추측: 미사용).
- **트랜잭션 ID 또는 EXEC PGM**: CICS 트랜잭션 ID **`CT02`** (37행). 매 턴 종료 시 `EXEC CICS RETURN TRANSID('CT02')`로 자기 자신 재기동(pseudo-conversational).

---

## 핵심 로직 흐름

이 프로그램은 CardDemo 공통 **pseudo-conversational 골격**을 따른다 (한 번의 키 입력 = 한 번의 프로그램 실행, 상태는 COMMAREA로 직렬화).

1. **MAIN-PARA 진입** (107행):
   - 에러 플래그 초기화, 메시지 클리어.
   - `EIBCALEN = 0`(최초·직접 진입, commarea 없음) → `COSGN00C`로 복귀(`RETURN-TO-PREV-SCREEN`) (115~117행).
   - 그 외 → `DFHCOMMAREA`를 `CARDDEMO-COMMAREA`로 복사 (119행).
2. **첫 표시 vs 재진입 분기** (`CDEMO-PGM-REENTER` 88-level 플래그, 120행):
   - **첫 표시**: 화면을 LOW-VALUES로 클리어, 커서를 계정ID에 위치(`MOVE -1`). 메뉴에서 거래를 선택해 온 경우(`CDEMO-CT02-TRN-SELECTED` 채워짐)면 그 값을 카드번호로 넣고 `PROCESS-ENTER-KEY` 선실행. 그 후 `SEND-TRNADD-SCREEN` (122~130행).
   - **재진입**: `RECEIVE-TRNADD-SCREEN`으로 입력 수신 후 `EVALUATE EIBAID`로 키 분기 (132~152행):
     - `DFHENTER` → `PROCESS-ENTER-KEY`
     - `DFHPF3` → 복귀(이전 프로그램 또는 `COMEN01C`)
     - `DFHPF4` → `CLEAR-CURRENT-SCREEN`(전 필드 초기화 후 재표시)
     - `DFHPF5` → `COPY-LAST-TRAN-DATA`(직전 거래 복사)
     - 그 외 키 → "Invalid key" 메시지.
3. **PROCESS-ENTER-KEY** (164행):
   - `VALIDATE-INPUT-KEY-FIELDS` → `VALIDATE-INPUT-DATA-FIELDS` 순서로 검증.
   - 이어 `CONFIRMI` 값으로 분기: `Y/y`면 `ADD-TRANSACTION` 수행, `N/n/공백`이면 "확인하세요" 안내, 그 외면 "Y/N만 유효" 에러 (169~188행).
4. **VALIDATE-INPUT-KEY-FIELDS** (193행): 계정ID가 있으면 숫자검증 후 `CXACAIX` 조회→카드번호 채움; 없고 카드번호가 있으면 숫자검증 후 `CCXREF` 조회→계정ID 채움; 둘 다 없으면 에러 (195~230행).
5. **VALIDATE-INPUT-DATA-FIELDS** (235행): 이미 에러가 있으면 데이터 필드 비움(238~249행). 이어 각 필드 **비어있음 검사** → 유형/카테고리 **숫자 검사** → 금액 **포맷 검사(`-99999999.99`)** → 발생/처리일자 **포맷 검사(`YYYY-MM-DD`)**, 부분참조(`(1:4)` 등)로 자릿수 단위 확인 (251~381행). 금액은 `FUNCTION NUMVAL-C`로 수치화해 다시 편집포맷으로 표시(383~386행). 발생/처리일자는 각각 `CSUTLDTC` 호출로 실제 날짜 검증(`SEV-CD='0000'` 정상, `MSG-NUM='2513'`은 허용 예외) (389~427행). 마지막으로 가맹점ID 숫자 검사 (430~436행).
6. **ADD-TRANSACTION** (442행) — 신규 거래 생성·저장:
   - `MOVE HIGH-VALUES TO TRAN-ID` 후 STARTBR→READPREV→ENDBR로 **가장 큰 기존 TRAN-ID** 획득 (444~448행).
   - `ADD 1`로 새 ID 산출, `INITIALIZE TRAN-RECORD` 후 화면 입력값을 레코드 필드에 매핑 (449~465행).
   - `WRITE-TRANSACT-FILE`로 기록. 성공 시 전 필드 초기화 + "Transaction added successfully. Your Tran ID is …" 녹색 메시지, 중복키면 "already exist", 기타 오류 메시지 (466, 711~749행).
7. **COPY-LAST-TRAN-DATA** (`PF5`, 471행): 키 필드 검증 후 직전(최대 키) 거래를 읽어 화면 입력 필드에 채워 넣고(템플릿), `PROCESS-ENTER-KEY`로 흐름 이어감 (473~495행).
8. **매 턴 종료**: `SEND-TRNADD-SCREEN`(516행) 또는 MAIN-PARA 끝의 `EXEC CICS RETURN TRANSID('CT02') COMMAREA(...)`로 종료, 다음 키 입력을 기다림 (156~159, 530~534행).

> 흐름 주의: 검증 패러그래프들은 오류 발견 시 곧장 `PERFORM SEND-TRNADD-SCREEN`을 호출한다. 그런데 `SEND-TRNADD-SCREEN`은 화면을 보내고 **자체적으로 `EXEC CICS RETURN`까지 실행**하므로(530행), 오류가 나면 그 시점에서 트랜잭션이 종료되어 이후 검증으로 진행하지 않고 사용자 입력을 다시 기다린다. 즉 `SEND-TRNADD-SCREEN` 안의 `RETURN`이 사실상 "검증 실패 시 조기 반환(early return)" 역할을 한다.

---

## Java/현대화 노트

- **pseudo-conversational ↔ 무상태 요청/응답**: 한 번의 `RETURN` 사이에는 메모리에 아무 것도 남지 않는다. `CARDDEMO-COMMAREA`는 직렬화된 세션 토큰/플래시 스코프와 동형. Java로는 `@SessionScope` DTO 또는 요청마다 클라이언트가 되돌려보내는 상태 객체로 매핑. `CDEMO-PGM-REENTER`(첫 표시 vs 재진입)는 "GET으로 폼 렌더 vs POST로 폼 제출" 구분에 해당.

- **검증 실패 = 조기 반환(숨은 제어흐름)**: `SEND-TRNADD-SCREEN` 내부의 `EXEC CICS RETURN`이 트랜잭션을 끝내므로, COBOL상으로는 한 패러그래프를 끝까지 PERFORM하는 것처럼 보여도 실제로는 첫 오류에서 멈춘다. Java로 옮길 때는 이 동작을 **검증기에서 첫 위반 시 예외 throw 또는 `return`**으로 명시화해야 한다. 그대로 순차 if문으로 옮기면 "여러 오류를 모두 누적"하는 다른 동작이 되므로 주의.

  ```java
  // COBOL의 "첫 오류에서 화면 재전송 후 RETURN" → Java 조기 반환
  if (isBlank(typeCd))   { addError(req, "Type CD can NOT be empty..."); return; }
  if (!isNumeric(typeCd)){ addError(req, "Type CD must be Numeric...");  return; }
  // ...
  ```

- **거래 ID 채번 = 경합 위험(중요)**: `HIGH-VALUES`로 STARTBR→READPREV로 "현재 최대 TRAN-ID + 1"을 쓰는 방식(444~449행)은 **동시성에 취약**하다. CICS 단일 트랜잭션 격리에 기대고 있을 뿐, 분산/멀티스레드 환경에서는 두 요청이 같은 ID를 채번할 수 있다. Java 이식 시 DB `SEQUENCE`/`IDENTITY`, 또는 원자적 카운터로 대체 권장. WRITE의 `DUPREC/DUPKEY` 분기(735행)는 이 경합에 대한 사후 방어다.

- **TRAN-ID는 숫자처럼 다루지만 PIC X(16)**: 레코드 정의상 `TRAN-ID PIC X(16)`(문자) (CVTRA05Y 5행)인데, 채번 시 `WS-TRAN-ID-N PIC 9(16)`로 옮겨 `ADD 1`을 한다(57, 448~451행). 즉 "16자리 숫자 문자열"을 정수로 캐스팅해 증가시키는 패턴. Java에서는 `long`(최대 16자리는 long 범위 내) 또는 `BigInteger`로 다루되, 저장 시 좌측 0 패딩된 16자리 문자열로 포맷해야 원래 포맷이 유지된다.

- **금액 PIC S9(9)V99 → BigDecimal**: `TRAN-AMT PIC S9(09)V99`(CVTRA05Y 10행)는 정수부 9자리·소수부 2자리의 고정소수점(부호 포함). 반드시 `BigDecimal`(scale=2)로 매핑하고 `double/float` 금지. 화면 입력은 `+99999999.99` 편집 포맷 문자열이며 `FUNCTION NUMVAL-C`로 수치화(383행) → Java에서는 로캘/통화 파싱으로 대응.

- **날짜 검증을 외부 유틸 위임**: `CSUTLDTC`(393, 413행)는 일자 유효성(윤년·월말 등)을 판정하는 공통 루틴. `SEV-CD='0000'`=정상, `MSG-NUM='2513'`은 예외적으로 통과 처리하는데 이는 특정 경고코드를 허용하는 것으로 보인다 (추측: 시간부 결측 등 비치명 경고). Java에서는 `LocalDate.parse(..., DateTimeFormatter.ofPattern("yyyy-MM-dd"))` + `try/catch`로 단순화 가능.

- **부분참조(reference modification)로 포맷 검사**: `TRNAMTI(1:1)`, `TORIGDTI(5:1)` 같은 `(시작:길이)` 구문(340~373행)은 고정폭 문자열의 특정 위치 문자를 검사하는 것. Java에서는 정규식(`^[-+]\\d{8}\\.\\d{2}$`, `^\\d{4}-\\d{2}-\\d{2}$`)으로 대체하면 더 명확하다.

- **두 교차참조 파일의 비대칭**: 계정ID→카드(`CXACAIX`), 카드→계정(`CCXREF`)은 같은 `CARD-XREF-RECORD` 레이아웃을 키만 달리해 조회한다(보조 인덱스 vs 기본). Java에서는 카드/계정 교차참조 테이블 하나에 두 인덱스(또는 두 조회 메서드)로 통합 가능.

- **불확실/미사용 의존성**: `CVACT01Y`(ACCOUNT-RECORD)와 `ACCTDAT` 파일명 리터럴은 선언만 있고 PROCEDURE에서의 실제 사용은 확인되지 않았다 (추측: 잔존 코드). 마이그레이션 인벤토리 작성 시 실사용 여부를 재확인할 것.

- **EBCDIC/고정길이/1-based**: TRANSACT는 RECLN 350 고정길이 VSAM(CVTRA05Y), 모든 문자필드는 공백 채움 고정폭. Java POJO로 옮길 때 trim 정책과 좌패딩 규칙을 명시하고, 메인프레임 원본 비교 시 EBCDIC↔UTF-8 변환을 고려할 것.
