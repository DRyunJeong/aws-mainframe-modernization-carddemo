# COACTUPC — 계정 정보 수정 (Account Update) 온라인 화면

- **유형**: CICS 온라인 COBOL
- **한 줄 요약**: 계정번호(11자리)로 계정/고객 마스터를 조회해 화면에 보여주고, 사용자가 수정한 계정·고객 정보를 다수의 입력 검증과 낙관적 잠금(optimistic lock) 처리를 거쳐 ACCTDAT/CUSTDAT VSAM 파일에 갱신하는 pseudo-conversational 트랜잭션.

## 기능 설명

`COACTUPC`(트랜잭션 ID `CAUP`)는 CardDemo의 계정 정보 수정 화면이다. 헤더 주석(L2-5)에 "Accept and process ACCOUNT UPDATE"로 명시되어 있다. 한 화면(`COACTUP` 맵셋의 `CACTUPA` 맵)에서 계정 마스터(잔액/한도/날짜/그룹 등)와 연결된 고객 마스터(이름/주소/전화/SSN/생년월일/FICO 등)를 함께 편집한다.

이 프로그램은 단일 트랜잭션 안에서 여러 단계(state machine)를 거친다. 상태는 commarea의 `ACUP-CHANGE-ACTION` 필드(L654-668)에 보관되는 1바이트 코드로 표현된다:

- `ACUP-DETAILS-NOT-FETCHED` (LOW-VALUES/SPACES): 아직 계정을 조회하지 않음 → 검색 키 입력 단계
- `ACUP-SHOW-DETAILS` ('S'): 조회된 원본 값을 화면에 표시한 상태
- `ACUP-CHANGES-OK-NOT-CONFIRMED` ('N'): 입력 검증을 통과했고 저장 확인(F5) 대기
- `ACUP-CHANGES-NOT-OK` ('E'): 입력 검증 실패
- `ACUP-CHANGES-OKAYED-AND-DONE` ('C'): DB 커밋 완료
- `ACUP-CHANGES-OKAYED-LOCK-ERROR` ('L') / `ACUP-CHANGES-OKAYED-BUT-FAILED` ('F'): 저장 실패

전형적인 사용자 흐름:
1. (계정번호 입력 → ENTER) 검색 키 검증 후 계정·고객 데이터를 읽어 화면에 표시.
2. (필드 수정 → ENTER) 입력값 검증. 통과하면 "F5를 눌러 저장" 확인 메시지.
3. (F5) 낙관적 잠금으로 레코드를 다시 읽어 변경 여부를 재확인하고 `REWRITE`로 갱신.
4. F3은 종료(호출 프로그램 또는 메뉴로 복귀), F12는 변경 취소(원본 재표시).

## 입력 / 출력

- **입력**:
  - BMS 맵 `CACTUPA`(맵셋 `COACTUP`)의 화면 입력 필드 `CACTUPAI`. 검색 키는 계정번호 `ACCTSIDI`(L1051), 나머지는 계정/고객 편집 필드(L1065-1424). 입력값이 `'*'` 또는 SPACES이면 LOW-VALUES(미입력)로 간주(L1051-1058 등).
  - commarea: 앞부분 `CARDDEMO-COMMAREA`(공용 세션, `COPY COCOM01Y`) + 뒷부분 `WS-THIS-PROGCOMMAREA`(이 프로그램 전용 상태 + 조회/수정 전후 값). `0000-MAIN`에서 `DFHCOMMAREA`를 두 영역으로 분리해 받음(L888-892).
  - `EIBAID`(눌린 PF 키, `COPY DFHAID`), `EIBCALEN`(commarea 길이).
- **출력**:
  - BMS 맵 출력 `CACTUPAO`로 화면 전송(`3400-SEND-SCREEN`, L3594-3601). 조회된 원본/수정 값, 정보 메시지(`INFOMSGO`), 에러 메시지(`CCARD-ERROR-MSG`)를 표시.
  - VSAM 파일 갱신: 계정 마스터 `ACCTDAT`와 고객 마스터 `CUSTDAT`에 `REWRITE`(L4065-4071, L4085-4091).
  - 갱신된 commarea를 `EXEC CICS RETURN`으로 다음 턴에 전달(L1015-1019).

## 의존성

- **COPY (카피북)**:
  - `CSUTLDWY` (L166) — 날짜 편집용 작업 변수 (CCYYMMDD)
  - `CVCRD01Y` (L597) — 공용 작업 변수(`CC-WORK-AREA`, `CC-ACCT-ID` 등)
  - `CSLKPCDY` (L602) — 북미 전화 지역번호/주(state) 코드 룩업 테이블
  - `DFHBMSCA` (L615) — BMS 화면 속성 상수 (DFHBMPRF/DFHBMFSE/DFHBMASB 등)
  - `DFHAID` (L616) — EIBAID 키 상수 (DFHENTER/DFHPF3 등)
  - `COTTL01Y` (L620) — 화면 제목
  - `COACTUP` (L623) — 계정 수정 화면 symbolic map (`CACTUPAI`/`CACTUPAO`)
  - `CSDAT01Y` (L626) — 현재 날짜/시간 작업 변수
  - `CSMSG01Y` (L629) — 공통 메시지 / `CSMSG02Y` (L632) — abend 변수(`ABEND-DATA` 등)
  - `CSUSR01Y` (L635) — 로그인 사용자 데이터(`SEC-USER-DATA`)
  - `CVACT01Y` (L640) — 계정 레코드 `ACCOUNT-RECORD` (RECLN 300)
  - `CVACT03Y` (L643) — 카드 교차참조 `CARD-XREF-RECORD`
  - `CVCUS01Y` (L646) — 고객 레코드 `CUSTOMER-RECORD` (RECLN 500)
  - `COCOM01Y` (L650) — 공용 commarea `CARDDEMO-COMMAREA`
  - `CSSETATY` (L3382 외 다수, REPLACING으로 필드별 전개) — 검증 결과에 따라 화면 필드 색상/속성을 설정하는 코드 조각
  - `CSSTRPFY` (L4199) — PF 키 정규화 공통 코드(`YYYY-STORE-PFKEY`)
  - `CSUTLDPY` (L4232) — 공통 날짜 검증 루틴(`EDIT-DATE-CCYYMMDD`, `EDIT-DATE-OF-BIRTH` 등)
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `EXEC CICS XCTL`로 복귀 대상에 제어 이양(L956-959). 대상은 `CDEMO-TO-PROGRAM` = 호출자(`CDEMO-FROM-PROGRAM`) 또는 기본값 메뉴 `COMEN01C`(`LIT-MENUPGM`, L939). F3 종료 시 사용.
  - `LINKAGE`로 호출되는 하위 프로그램(`CALL`)은 없음. 날짜 검증 등은 모두 COPY로 인라인된 paragraph이다.
  - (참고) WORKING-STORAGE에 `COCRDUPC`/`COCRDLIC`/`COCRDSLC` 등 카드 관련 프로그램 리터럴이 정의(L541-572)되어 있으나, 이 소스의 PROCEDURE DIVISION에서 해당 프로그램으로 XCTL/LINK하는 경로는 보이지 않음 (추측: 향후/공유 복사 잔재).
- **데이터셋/파일/DB 테이블** (모두 CICS VSAM, DB2/SQL 없음):
  - `CXACAIX` (`LIT-CARDXREFNAME-ACCT-PATH`, L581) — 카드 교차참조의 **계정ID 대체 인덱스(AIX)**. 계정번호로 고객ID/카드번호를 얻기 위해 READ (L3654-3662).
  - `ACCTDAT` (`LIT-ACCTFILENAME`, L573) — 계정 마스터. 조회 READ(L3703) 및 갱신용 `READ UPDATE`→`REWRITE`(L3894, L4066).
  - `CUSTDAT` (`LIT-CUSTFILENAME`, L575) — 고객 마스터. 조회 READ(L3753) 및 갱신용 `READ UPDATE`→`REWRITE`(L3921, L4086).
  - (정의는 있으나 미사용: `CARDDAT`/`CARDAIX`, L577-579.)
- **트랜잭션 ID 또는 EXEC PGM**:
  - 트랜잭션 ID `CAUP` (`LIT-THISTRANID`, L535). 매 턴 종료 시 `RETURN TRANSID('CAUP')`로 같은 트랜잭션 재기동(L1015-1019).
  - 프로그램명 `COACTUPC` (`LIT-THISPGM`, L533), 맵셋 `COACTUP`/맵 `CACTUPA`.

## 핵심 로직 흐름

**0000-MAIN (진입점, L859-1005)**
1. `EXEC CICS HANDLE ABEND LABEL(ABEND-ROUTINE)`로 비정상 종료 핸들러 등록(L862-864).
2. `EIBCALEN = 0`이거나 메뉴에서 막 진입(첫 진입)이면 commarea 초기화 + `CDEMO-PGM-ENTER`/`ACUP-DETAILS-NOT-FETCHED` 설정. 아니면 `DFHCOMMAREA`를 `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA`로 분리 복원(L880-893).
3. `YYYY-STORE-PFKEY`(COPY `CSSTRPFY`)로 눌린 PF 키 정규화(L898-899).
4. AID 유효성 검사: 현재 상태에서 의미 있는 키(ENTER, F3, 조건부 F5/F12)만 허용. 그 외엔 ENTER로 강제(L905-916).
5. `EVALUATE TRUE`로 분기(L921-1004):
   - **F3** → 종료. `CDEMO-TO-PROGRAM`/`CDEMO-TO-TRANID`를 호출자(없으면 메뉴)로 설정, `SYNCPOINT` 후 `XCTL`(L927-959).
   - **최초 진입(미조회)** → `WS-THIS-PROGCOMMAREA` 초기화 후 `3000-SEND-MAP`으로 검색 화면 표시, `GO TO COMMON-RETURN`(L964-973).
   - **저장 완료/실패 상태** → 화면·검색키 초기화 후 새 검색 화면 표시(L979-989).
   - **그 외(WHEN OTHER)** → 본 처리 3종 호출: `1000-PROCESS-INPUTS` → `2000-DECIDE-ACTION` → `3000-SEND-MAP`(L996-1003).
6. **COMMON-RETURN** (L1007-1020): 에러 메시지 세팅 후 `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA`를 `WS-COMMAREA`로 합쳐 `EXEC CICS RETURN TRANSID('CAUP') COMMAREA(...)`. (pseudo-conversational: 다음 키 입력 시 같은 트랜잭션 재기동.)

**1000-PROCESS-INPUTS (L1025-1038)**
- `1100-RECEIVE-MAP`(L1039): `RECEIVE MAP`으로 화면을 받아 모든 입력 필드를 `ACUP-NEW-*`로 이동. 통화 필드(한도/잔액/사이클)는 `FUNCTION TEST-NUMVAL-C`로 숫자성 확인 후 `NUMVAL-C`로 변환(L1078-1084 등). 미조회 상태면 계정번호만 받고 조기 종료(L1060-1062).
- `1200-EDIT-MAP-INPUTS`(L1429): 검증 디스패처.
  - 미조회 상태면 `1210-EDIT-ACCOUNT`(계정번호: 미입력/비숫자/0/11자리 검증, L1783-1822)만 수행하고 종료.
  - 조회 후 상태면 `1205-COMPARE-OLD-NEW`(L1681)로 원본(`ACUP-OLD-*`) 대비 변경 여부 판정. 변경 없으면 `NO-CHANGES-DETECTED` 후 종료(L1463-1468).
  - 변경이 있으면 필드별 검증 호출: Y/N(`1220`), 날짜 CCYYMMDD(COPY 루틴), 부호 9V2 통화(`1250`), SSN(`1265`, part1 000/666/900-999 금지), 생년월일(`EDIT-DATE-OF-BIRTH`), FICO 300-850(`1275`), 이름 alpha(`1225`/`1235`), 주소 필수(`1215`), 주 코드(`1270`), 우편번호 숫자(`1245`), 전화번호(`1260`, 지역번호 룩업 검증), 주-우편번호 교차검증(`1280`)(L1470-1669). 오류 없으면 `ACUP-CHANGES-OK-NOT-CONFIRMED` 설정(L1671-1675).

**2000-DECIDE-ACTION (L2562-2645)** — 현재 상태 + 키에 따라 다음 행동 결정(`EVALUATE TRUE`):
- 미조회 또는 **F12**(취소)이고 계정필터 유효 → `9000-READ-ACCT`로 조회, 성공 시 `ACUP-SHOW-DETAILS`(L2568-2580).
- `ACUP-SHOW-DETAILS`에서 오류·무변경 아니면 `ACUP-CHANGES-OK-NOT-CONFIRMED`(L2585-2591).
- `ACUP-CHANGES-OK-NOT-CONFIRMED` + **F5** → `9600-WRITE-PROCESSING` 호출 후 결과로 상태 전이(잠금실패 'L'/갱신실패 'F'/동시변경→재표시/성공 'C')(L2602-2615).
- 예상치 못한 시나리오는 `ABEND-ROUTINE` 호출(L2633-2640).

**9000-READ-ACCT 체인 (조회, L3608-3887)**
1. `9200-GETCARDXREF-BYACCT`: `CXACAIX`(AIX) READ로 계정→고객ID/카드번호 획득(L3654-3696). NOTFND면 에러.
2. `9300-GETACCTDATA-BYACCT`: `ACCTDAT` READ(L3703-3746).
3. `9400-GETCUSTDATA-BYCUST`: `CUSTDAT` READ(L3753-3795).
4. `9500-STORE-FETCHED-DATA`: 읽은 값을 commarea 컨텍스트(`CDEMO-*`)와 원본 보관영역 `ACUP-OLD-*`로 저장(L3801-3884). 날짜는 `(1:4)/(6:2)/(9:2)` 참조수정으로 연/월/일 분해.

**9600-WRITE-PROCESSING (저장, 낙관적 잠금, L3888-4107)**
1. `READ ... UPDATE`로 `ACCTDAT` 잠금 획득(L3894-3903). 실패 시 `COULD-NOT-LOCK-ACCT-FOR-UPDATE`.
2. `READ ... UPDATE`로 `CUSTDAT` 잠금 획득(L3921-3930). 실패 시 `COULD-NOT-LOCK-CUST-FOR-UPDATE`.
3. `9700-CHECK-CHANGE-IN-REC`(L4109): 방금 잠금 읽은 현재 레코드를 조회 당시 보관한 `ACUP-OLD-*`와 필드 단위로 비교. 다르면 `DATA-WAS-CHANGED-BEFORE-UPDATE`(다른 사용자가 그사이 변경 → 갱신 중단, 사용자에게 재검토 요청).
4. 변경 없으면 `ACCT-UPDATE-RECORD`/`CUST-UPDATE-RECORD`를 새 값으로 구성(날짜는 `STRING`으로 `YYYY-MM-DD`, 전화는 `(NPA)NXX-XXXX` 포맷)(L3956-4059).
5. `REWRITE ACCTDAT`(L4065). 실패 시 `LOCKED-BUT-UPDATE-FAILED`.
6. `REWRITE CUSTDAT`(L4085). 실패 시 `LOCKED-BUT-UPDATE-FAILED` + `EXEC CICS SYNCPOINT ROLLBACK`(L4099-4101)로 앞선 계정 갱신까지 롤백.

**3000-SEND-MAP (L2649-2666)**: 화면 초기화(`3100`) → 변수 채움(`3200`, 상태별 초기/원본/수정값) → 정보메시지(`3250`) → 필드 속성/색상(`3300`, COPY `CSSETATY`) → 메시지 속성(`3390`) → `3400-SEND-SCREEN`(SEND MAP).

## Java/현대화 노트

- **State machine 명시화**: `ACUP-CHANGE-ACTION`의 1바이트 코드는 사실상 화면 워크플로의 상태이다. Java로는 `enum AccountUpdateState { SEARCH, SHOW_DETAILS, AWAITING_CONFIRM, COMMITTED, LOCK_ERROR, UPDATE_FAILED }`로 모델링하고, `2000-DECIDE-ACTION`의 `EVALUATE`는 상태×이벤트(PF키)를 받는 전이 함수(switch 또는 상태 패턴)로 옮기는 것이 자연스럽다.

- **pseudo-conversational ↔ 무상태 웹 요청**: `RETURN TRANSID + COMMAREA`는 HTTP 요청 사이에 직렬화된 세션/뷰모델을 주고받는 것과 동형이다. `WS-THIS-PROGCOMMAREA`(전/후 값 + 상태)는 화면 폼의 hidden state 또는 서버 세션에 보관하는 DTO에 대응. Java에서는 Spring MVC의 세션 스코프 모델 또는 stateless라면 서명된 토큰/폼 필드로 round-trip.

- **낙관적 잠금(optimistic concurrency)**: 이 프로그램의 핵심 패턴이다. 조회 시 원본을 `ACUP-OLD-*`에 보관하고, 저장 직전 `READ UPDATE`로 다시 읽어 `9700-CHECK-CHANGE-IN-REC`로 필드별 재비교한다. 다르면 "Record changed by some one else"로 거부(L521-522). Java/JPA의 `@Version` 낙관적 락과 동일 목적이나, 여기서는 버전 컬럼이 없어 **전체 필드 비교**로 구현했다는 점이 다르다. 단, `READ UPDATE`는 그 자체로 VSAM 레코드에 비관적 락도 건다(읽고-비교-쓰기 사이를 보호). 마이그레이션 시 `@Version` 컬럼 도입을 강력 추천.

- **트랜잭션 원자성**: 계정·고객 두 파일을 갱신하며, 두 번째(`CUSTDAT`) `REWRITE` 실패 시 `SYNCPOINT ROLLBACK`으로 첫 갱신을 되돌린다(L4099-4101). F3 종료 직전 `SYNCPOINT`(L952-954)는 정상 커밋. Java에서는 `@Transactional` 한 단위로 두 update를 묶으면 동일 보장을 자동으로 얻는다.

- **`'*'`/SPACES = 미입력 규약(주의)**: 화면 필드가 `'*'` 또는 SPACES이면 LOW-VALUES(=값 없음/변경 안 함)로 취급한다(L1051-1058 패턴이 전 필드 반복). 이는 BMS 화면에서 보호/빈 필드를 표현하는 방식으로, Java에서는 `null` 또는 Optional로 매핑해야 한다. COBOL에는 native null이 없어 LOW-VALUES로 대용한 것임에 유의.

- **`REDEFINES`로 X(n)↔숫자 이중 뷰**: `ACUP-OLD-CURR-BAL PIC X(12)`와 `ACUP-OLD-CURR-BAL-N REDEFINES ... PIC S9(10)V99`처럼 동일 바이트를 문자/숫자 두 뷰로 본다(L675-707, L763-795). 문자 뷰는 commarea 직렬화/비교용, 숫자 뷰는 계산/표시용. Java에는 직접 등가물이 없으므로 단일 `BigDecimal` 필드 + 문자열 직렬화 로직으로 대체한다. `S9(10)V99`는 implied decimal(소수점 2자리)이므로 **반드시 `BigDecimal`** (float/double 금지).

- **숫자 변환 함수**: `FUNCTION TEST-NUMVAL-C`(검증)/`NUMVAL-C`(파싱)는 통화 기호·콤마·부호가 섞인 표시 문자열을 숫자로 안전 변환한다(L1078-1080 등). Java에서는 입력 파싱을 `BigDecimal` + 로캘 인지 `NumberFormat`/정규식 검증으로 옮긴다.

- **참조수정으로 날짜 분해/조립**: `ACCT-OPEN-DATE(1:4)`(연), `(6:2)`(월), `(9:2)`(일) 식으로 `YYYY-MM-DD` 문자열을 잘라 쓰고(L3832-3834), 저장 시 `STRING ... '-' ...`로 다시 조립(L3976-3982). Java에서는 `LocalDate` + `DateTimeFormatter("yyyy-MM-dd")`로 대체. (주의: L4174-4179의 동시변경 비교에서 `ACUP-OLD-CUST-DOB-YYYY-MM-DD`의 월/일을 `(5:2)`/`(7:2)`로 참조하는데, 보관 포맷이 8자리 `YYYYMMDD`(구분자 없음)이므로 월=`(5:2)`·일=`(7:2)`이 맞다. 반면 현재 레코드 `CUST-DOB-YYYY-MM-DD`는 `(6:2)`/`(9:2)`(구분자 있는 10자리)로 참조 — 두 포맷이 섞여 있으니 마이그레이션 시 `LocalDate` 단일 표현으로 통일 필요.)

- **GO TO 다수 사용**: 검증/IO 루틴이 오류 시 `GO TO ...-EXIT`로 조기 탈출하는 구조가 반복된다(전형적 COBOL 가드절). Java에서는 early `return` 또는 예외로 자연스럽게 표현된다. 특히 `9700`의 `GO TO 9600-WRITE-PROCESSING-EXIT`(L4144, L4190)는 **다른 paragraph의 EXIT로 점프**하는데(PERFORM THRU 범위에 의존), 이는 Java로 직역 불가하므로 호출 구조를 메서드 + 반환 코드/예외로 재설계해야 한다.

- **에러/정보 메시지의 88-level 카탈로그**: `WS-RETURN-MSG`/`WS-INFO-MSG`의 88-level 조건명(L464-528)이 사용자 메시지 상수 집합이다. Java에서는 메시지 enum 또는 i18n 리소스 번들로 옮기기 좋다.

- **불확실/주의점**:
  - WORKING-STORAGE의 카드 관련 프로그램/파일 리터럴(`COCRDUPC`, `CARDDAT` 등)은 이 소스에서 실제로 사용되지 않는다 (추측: 공유 템플릿에서 복사된 잔재). 마이그레이션 시 죽은 코드로 정리 가능.
  - `1260-EDIT-US-PHONE-NUM`의 "not mandatory" 판정 조건(L2234-2239)에 `WS-EDIT-US-PHONE-NUMA`가 중복 비교되어 `...NUMC` 검사가 누락된 것으로 보인다 (추측: 원본 버그). 재작성 시 정확한 "세 부분 모두 공백" 검사로 수정 권장.
