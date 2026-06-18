# COCRDUPC — 신용카드 상세 수정 (Credit Card Update)

- **유형**: CICS 온라인 COBOL
- **한 줄 요약**: 계정번호+카드번호로 카드 레코드를 조회하여 화면에 표시하고, 사용자가 입력한 변경값을 검증/확인 후 낙관적 잠금(optimistic locking) 방식으로 CARDDAT VSAM 파일에 갱신하는 pseudo-conversational 트랜잭션(CCUP).

## 기능 설명

`COCRDUPC`는 CardDemo의 신용카드 상세 정보 **수정** 화면을 담당하는 온라인 프로그램이다. 헤더 주석(라인 1-5)에 "Accept and process credit card detail request"로 명시되어 있고, 비즈니스 로직 계층(Layer: Business logic)으로 분류된다.

처리 흐름은 화면 상태를 나타내는 `CCUP-CHANGE-ACTION` 플래그(라인 276-290)를 중심으로 다음 단계를 거친다:

1. **검색 키 입력 단계** — 계정번호(11자리)와 카드번호(16자리)를 사용자에게 요청.
2. **상세 표시 단계** (`CCUP-SHOW-DETAILS` 'S') — CARDDAT에서 읽은 카드의 현재 값(이름/상태/유효기간)을 수정 가능한 형태로 표시.
3. **변경 검증 단계** (`CCUP-CHANGES-NOT-OK` 'E' / `CCUP-CHANGES-OK-NOT-CONFIRMED` 'N') — 입력값을 편집 검증하고, 통과 시 "F5를 눌러 저장" 확인을 요청.
4. **저장 단계** (`CCUP-CHANGES-OKAYED-AND-DONE` 'C' / `CCUP-CHANGES-FAILED` 'L'·'F') — F5 확인 시 REWRITE로 커밋. 저장 직전 레코드가 타인에 의해 변경되었는지 재검사.

화면 진입은 두 경로다: (a) 메뉴(COMEN01C)에서 직접 진입 → 검색 키부터 입력받음, (b) 카드 목록 화면(COCRDLIC)에서 진입 → 이미 보유한 계정/카드 키로 곧바로 상세를 조회해 표시(라인 482-497).

## 입력 / 출력

- **입력**:
  - BMS 맵 `CCRDUPA`(맵셋 `COCRDUP`)에서 `EXEC CICS RECEIVE MAP ... INTO(CCRDUPAI)`로 수신하는 화면 입력 필드(라인 579-584): 계정번호(`ACCTSIDI`), 카드번호(`CARDSIDI`), 카드명(`CRDNAMEI`), 활성상태(`CRDSTCDI`), 유효월(`EXPMONI`)·유효년(`EXPYEARI`)·유효일(`EXPDAYI`).
  - `DFHCOMMAREA`(라인 362-364)로 전달되는 세션 상태: 앞부분은 공용 `CARDDEMO-COMMAREA`, 뒷부분은 프로그램 고유 `WS-THIS-PROGCOMMAREA`(라인 396-400).
  - `EIBCALEN`, `EIBAID`(누른 키), VSAM 파일 `CARDDAT`의 카드 레코드.
- **출력**:
  - BMS 맵 `CCRDUPA`로 `EXEC CICS SEND MAP ... FROM(CCRDUPAO)`(라인 1329-1336) — 카드 상세, 정보 메시지(`INFOMSGO`), 오류 메시지(`ERRMSGO`), 필드 색상(`DFHRED` 등) 포함.
  - VSAM 파일 `CARDDAT`에 `EXEC CICS REWRITE`(라인 1477-1483)로 변경된 카드 레코드(`CARD-UPDATE-RECORD`, 라인 314-321) 기록.
  - `EXEC CICS RETURN ... COMMAREA(WS-COMMAREA)`(라인 554-558)로 갱신된 세션 상태 반환.

## 의존성

- **COPY (카피북)**:
  - `CVCRD01Y` — `CC-WORK-AREA`(AID 88레벨, `CCARD-NEXT-PROG`, 검색 키 `CC-ACCT-ID`/`CC-CARD-NUM` 및 숫자 REDEFINES), 라인 268.
  - `COCOM01Y` — 공용 `CARDDEMO-COMMAREA`(프로그램 간 세션/내비게이션 상태), 라인 272.
  - `DFHBMSCA`(BMS 화면 속성 상수), `DFHAID`(EIBAID 키 상수), 라인 327-328.
  - `COTTL01Y`(화면 제목), `COCRDUP`(BMS 심볼릭 맵 `CCRDUPAI`/`CCRDUPAO`), 라인 332·334.
  - `CSDAT01Y`(현재일자), `CSMSG01Y`(공통 메시지), `CSMSG02Y`(ABEND 변수 `ABEND-DATA`/`ABEND-MSG` 등), `CSUSR01Y`(로그인 사용자), 라인 337-346.
  - `CVACT02Y` — 카드 레코드 레이아웃 `CARD-RECORD`(RECLN 150), 라인 353.
  - `CVCUS01Y` — 고객 레이아웃, 라인 359. (선언만 있고 PROCEDURE에서 직접 사용은 확인되지 않음 — (추측) 향후 확장용.)
  - `CSSTRPFY` — PF키 매핑 공통 코드(`YYYY-STORE-PFKEY` 단락 본문), 라인 1528.
  - 참고: `CVACT01Y`(계정)·`CVACT03Y`(카드 교차참조)는 라인 350·356에서 주석 처리되어 **미사용**.
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)`(라인 473-476) — 종료/PF3 시 동적으로 결정된 호출원으로 이동. 호출원이 없으면 `LIT-MENUPGM` = `COMEN01C`(메인 메뉴)로, 있으면 `CDEMO-FROM-PROGRAM`(예: 카드 목록 `COCRDLIC`)으로 복귀(라인 449-454, 235-236).
  - 별도의 정적 `CALL`/`LINK`는 없음.
- **데이터셋/파일/DB 테이블**:
  - `CARDDAT`(`LIT-CARDFILENAME`, 라인 251) — 카드 마스터 VSAM. 키 = 카드번호(`WS-CARD-RID-CARDNUM`, 16자리)로 READ(라인 1382-1390)·READ UPDATE(라인 1427-1436)·REWRITE(라인 1477-1483).
  - `CARDAIX`(`LIT-CARDFILENAME-ACCT-PATH`, 라인 253) — 계정 기준 AIX(대체 인덱스). 리터럴만 정의되어 있고 실제 접근 코드는 주석 처리됨(라인 1379·1424). (추측) 계정 단독 조회용으로 의도되었으나 현재 경로는 카드번호 직접 READ만 사용.
  - DB2/SQL 등 관계형 DB 접근은 없음.
- **트랜잭션 ID 또는 EXEC PGM**:
  - 트랜잭션 ID `CCUP`(`LIT-THISTRANID`, 라인 221-222). `EXEC CICS RETURN TRANSID('CCUP')`로 다음 턴에 자기 자신을 재기동(pseudo-conversational).

## 핵심 로직 흐름

1. **0000-MAIN 진입**(라인 367): `HANDLE ABEND LABEL(ABEND-ROUTINE)` 등록 → 작업영역 초기화 → `WS-TRANID`에 'CCUP' 저장.
2. **commarea 복원**(라인 388-401): `EIBCALEN = 0`(최초 진입)이거나 "메뉴에서 왔고 재진입이 아님"이면 commarea 전체 초기화 후 `CDEMO-PGM-ENTER`/`CCUP-DETAILS-NOT-FETCHED` 설정. 그 외에는 `DFHCOMMAREA`를 **두 조각으로 분리** 복원 — 앞 `LENGTH OF CARDDEMO-COMMAREA` 바이트는 공용 commarea로, 그 뒤 바이트는 `WS-THIS-PROGCOMMAREA`(이 프로그램 전용 상태)로 이동.
3. **PF키 정규화 및 유효성**(라인 406-424): `YYYY-STORE-PFKEY`(COPY `CSSTRPFY`)로 EIBAID를 `CCARD-AID-*` 88레벨로 변환. 현재 상태에서 허용되는 키(ENTER, PF3, 조건부 PF5/PF12)가 아니면 ENTER로 강제 재매핑.
4. **메인 디스패치 `EVALUATE TRUE`**(라인 429-543):
   - **PF3 종료 / 작업 완료 후 목록화면 복귀**(라인 435-476): from/to 내비게이션 필드를 채우고 `EXEC CICS SYNCPOINT`(커밋) 후 `XCTL`로 호출원 또는 메뉴로 이동.
   - **카드목록(COCRDLIC)에서 진입 또는 PF12**(라인 482-497): 전달된 계정/카드 키로 `9000-READ-DATA` 수행 → `CCUP-SHOW-DETAILS` 설정 → `3000-SEND-MAP` → `COMMON-RETURN`.
   - **신규 진입(검색 키 미보유)**(라인 502-511): 빈 검색 화면 전송.
   - **저장 완료 또는 실패 후**(라인 517-528): 검색 키 초기화하고 새 검색 화면 전송.
   - **그 외(데이터가 표시된 상태에서 사용자 응답)**(라인 535-542): `1000-PROCESS-INPUTS` → `2000-DECIDE-ACTION` → `3000-SEND-MAP` → `COMMON-RETURN`.
5. **1000-PROCESS-INPUTS**(라인 564-): `1100-RECEIVE-MAP`으로 화면 수신(입력 '*'/공백은 `LOW-VALUES`로 정규화, 라인 589-635) → `1200-EDIT-MAP-INPUTS`로 검증.
6. **입력 검증 `1200`**(라인 641-715): 아직 데이터 미조회 상태면 검색 키(계정 `1210`, 카드 `1220`)만 검증. 이미 조회된 상태면 신규값과 기존값(`CCUP-OLD-CARDDATA`)을 `UPPER-CASE`로 비교해 **변경 없음** 판정(라인 680-683). 변경이 있으면 카드명(`1230`, 알파벳/공백만), 활성상태(`1240`, Y/N), 유효월(`1250`, 1~12), 유효년(`1260`, 1950~2099)을 차례로 검증. 모두 통과 시 `CCUP-CHANGES-OK-NOT-CONFIRMED` 설정.
7. **2000-DECIDE-ACTION**(라인 948-1028): 현재 상태별로 다음 행동 결정. 핵심은 `CCUP-CHANGES-OK-NOT-CONFIRMED AND PF5` 일 때 `9200-WRITE-PROCESSING`을 수행하고 결과(잠금실패/갱신실패/타인변경/성공)에 따라 상태 전이.
8. **9000-READ-DATA / 9100-GETCARD-BYACCTCARD**(라인 1343-1416): `CARDDAT`를 카드번호 키로 READ. NORMAL이면 카드 현재값을 `CCUP-OLD-*`(낙관적 잠금 기준선)에 저장하고 이름은 대문자로 정규화(라인 1356-1358). NOTFND/기타는 오류 메시지 설정.
9. **9200-WRITE-PROCESSING**(라인 1420-1496): `READ ... UPDATE`로 레코드 잠금(실패 시 `COULD-NOT-LOCK-FOR-UPDATE`) → `9300-CHECK-CHANGE-IN-REC`로 잠금 시점 레코드가 조회 시점(`CCUP-OLD-*`)과 동일한지 비교. 다르면 `DATA-WAS-CHANGED-BEFORE-UPDATE`로 갱신 중단(낙관적 잠금 충돌). 동일하면 `CARD-UPDATE-RECORD` 구성(유효기간은 `STRING ... DELIMITED BY SIZE`로 `YYYY-MM-DD` 조립, 라인 1467-1474) 후 `REWRITE`. 실패 시 `LOCKED-BUT-UPDATE-FAILED`.
10. **3000-SEND-MAP**(라인 1035-): 화면 초기화(`3100`, 제목/날짜/시간) → 변수 채움(`3200`) → 정보메시지 결정(`3250`) → 속성·색상·커서·보호여부 설정(`3300`, 오류 필드는 `DFHRED`+커서 이동, 빈 필드는 '*' 표시) → `3400-SEND-SCREEN`으로 전송.
11. **COMMON-RETURN**(라인 546-559): 두 commarea 조각을 `WS-COMMAREA`(2000바이트)에 다시 직렬화하여 `EXEC CICS RETURN TRANSID('CCUP') COMMAREA(...)`.
12. **ABEND-ROUTINE**(라인 1531-1556): 예외 시 `ABEND-DATA` 화면 전송 후 `EXEC CICS ABEND ABCODE('9999')`.

## Java/현대화 노트

- **pseudo-conversational ↔ 무상태 웹 요청**: `EXEC CICS RETURN TRANSID('CCUP') COMMAREA`는 매 키 입력마다 트랜잭션을 재기동하고 commarea로 상태를 복원하는 구조다. Java로는 HTTP 요청/응답 + 서버 세션(또는 직렬화된 상태 토큰)에 대응한다. `CCUP-CHANGE-ACTION`은 명시적 화면 상태머신이므로, Spring MVC라면 폼의 hidden state 필드나 세션 스코프 enum(`CardUpdateState { SEARCH, SHOW, VALIDATE, CONFIRM, DONE, FAILED }`)으로 모델링하는 것이 자연스럽다.

- **commarea 이중 구조(주의)**: 이 프로그램은 `DFHCOMMAREA`를 **앞=공용 `CARDDEMO-COMMAREA`, 뒤=프로그램 고유 `WS-THIS-PROGCOMMAREA`** 두 영역으로 수동 오프셋 분할한다(라인 396-400, 549-552). 이는 "공유 세션 객체 + 화면별 ViewState"를 한 바이트 버퍼에 직렬화한 것으로, Java에서는 별도의 두 DTO(`CardDemoSession` + `CardUpdateViewState`)로 깔끔히 분리하면 된다. 바이트 오프셋 산술(`LENGTH OF ... + 1`)에 의존하는 부분은 직렬화 라이브러리로 대체하면 사라진다.

- **낙관적 잠금(핵심 비즈니스 로직)**: 조회 시점 값을 `CCUP-OLD-*`에 보관해 두었다가, 저장 직전 `READ UPDATE`로 다시 읽은 실제 레코드와 필드별 비교(라인 1503-1508)하여 다르면 갱신을 거부한다("Record changed by some one else"). 이는 JPA `@Version` 기반 낙관적 잠금과 동일한 개념이며, Java에서는 버전 컬럼이나 `if-match` 비교 로직으로 그대로 옮길 수 있다. 단, 여기서는 전 필드 동등 비교라는 점에 유의.

- **REDEFINES = 같은 메모리의 다중 뷰**: `CC-ACCT-ID`(`PIC X(11)`)와 `CC-ACCT-ID-N`(`PIC 9(11)`, 라인 36), 유효기간 문자열을 연/월/일로 쪼개 보는 `CARD-EXPIRAION-DATE-X` REDEFINES(라인 116-123) 등은 Java에 직접 대응물이 없다. 문자열 검증용 뷰와 숫자 비교용 뷰를 한 저장소로 겹쳐 쓴 것이므로, Java에서는 `String` 원본 + 파싱한 `int`/`LocalDate`로 명시 변환하는 것이 안전하다.

- **고정 길이 / 패딩 / 88레벨**: 카드번호 16자리, 계정 11자리는 고정 길이 `String`이며 부족분은 공백/`LOW-VALUES`로 채운다. 입력 '*'·공백을 `LOW-VALUES`로 치환하는 관용구(라인 589-635)는 "미입력" 표현이다(COBOL에 native null이 없어서 사용하는 패턴). `88` 조건명(`VALID-MONTH VALUES 1 THRU 12`, `FLG-YES-NO-VALID VALUES 'Y','N'` 등)은 Java의 boolean 술어/`enum`/검증 어노테이션(`@Pattern`, 범위 체크)으로 옮긴다.

- **유효기간 조립**: `STRING CCUP-NEW-EXPYEAR '-' CCUP-NEW-EXPMON '-' CCUP-NEW-EXPDAY DELIMITED BY SIZE`(라인 1467-1474)는 `YYYY-MM-DD` 문자열 연결이다. Java에서는 `LocalDate.of(year, month, day)` 또는 `String.format`/`DateTimeFormatter`로 대체.

- **GO TO / 단락 폴스루(주의)**: 검증 단락들은 오류 시 `GO TO ...-EXIT`로 조기 탈출한다(구조적 early return). `2000-DECIDE-ACTION` 등은 `EVALUATE TRUE`의 `WHEN`이 폴스루 없이 단일 분기로 끝나도록 작성되어 있으나, COBOL 단락은 기본적으로 다음 단락으로 흘러가므로(예: `9300`의 `GO TO 9200-...-EXIT`, 라인 1518) 제어 흐름 이전 시 폴스루를 반드시 검토해야 한다. Java로 옮길 때는 각 검증을 `boolean validate...()` 메서드로 뽑고 switch/if-else로 명시 분기하면 폴스루 위험이 제거된다.

- **화면 속성 처리**: `DFHRED`(빨강), `DFHBMFSE`/`DFHBMPRF`(unprotected/protected), 커서 위치 `MOVE -1 TO ...L`(라인 1214 등)은 BMS 화면 제어다. 웹 UI에서는 검증 실패 필드 하이라이트·포커스·readonly 토글에 해당한다.

- **불완전성 메모**: `CARDAIX` 계정 AIX 접근(라인 1379·1424)과 `CVACT01Y`/`CVACT03Y`/`CVCUS01Y` 일부는 주석 처리되었거나 선언만 존재한다. 현재 동작 경로는 **카드번호 직접 키 READ**만 사용하므로, 계정번호는 화면 검증·표시·`CARD-UPDATE-ACCT-ID` 기록에만 쓰인다(조회 조건이 아님). 모더나이즈 시 "계정번호로의 조회"가 실제 요구사항인지 확인 필요.
