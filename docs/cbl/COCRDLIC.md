# COCRDLIC — 신용카드 목록 조회 화면

- **유형**: CICS 온라인 COBOL (pseudo-conversational, 페이지네이션 목록 화면)
- **한 줄 요약**: 신용카드를 7건씩 페이지 단위로 나열하고, 계정번호/카드번호로 필터링하며, 각 행에서 상세 조회(S) 또는 수정(U) 화면으로 분기시키는 트랜잭션 프로그램.

## 기능 설명

COCRDLIC는 트랜잭션 ID `CCLI`로 기동되는 온라인 카드 목록 화면이다. 프로그램 헤더 주석(L1-7)이 동작을 명확히 규정한다:

- 컨텍스트(commarea)가 없고 관리자(admin) 사용자면 → 모든 카드를 나열 (L4-6)
- 일반 사용자면 → commarea에 담긴 `ACCT`(계정)에 연관된 카드만 나열 (L7)

화면(`CCRDLIA` 맵)은 한 페이지에 최대 7개 카드 행을 보여준다(`WS-MAX-SCREEN-LINES VALUE 7`, L177-178). 각 행은 **선택칸 + 계정번호 + 카드번호 + 카드상태**로 구성되며, 상단에 계정번호/카드번호 **검색 필터** 입력란이 있다.

사용자는 다음 키로 상호작용한다 (L364-369 주석, L371-374):
- **Enter**: 현재 시작 키 기준 목록 조회 / 선택한 행 처리
- **PF3**: 종료 → 메인 메뉴(`COMEN01C`)로 복귀
- **PF7**: 페이지 업 (이전 7건)
- **PF8**: 페이지 다운 (다음 7건)

행 선택칸에 `S`를 입력하면 카드 상세 화면(`COCRDSLC`), `U`를 입력하면 카드 수정 화면(`COCRDUPC`)으로 `XCTL` 이동한다(L517-569). 한 번에 한 행만 선택해야 하며, 둘 이상 선택하면 오류 처리된다(L1084-1095).

> Java 비유: 이것은 페이지네이션이 있는 "카드 검색 결과 그리드" 화면이다. Spring MVC로 치면 `GET /cards?acctId=...&cardNum=...&page=N` 요청을 받아 `Page<CardRow>`(7개 단위)를 렌더링하고, 각 행에 "보기"/"수정" 링크를 거는 컨트롤러+뷰에 해당한다. 단, 페이징 상태가 서버 세션(commarea)에 저장된다는 점이 다르다.

## 입력 / 출력

- **입력**:
  - **commarea 두 부분**(L327-331): 앞부분은 공용 세션 상태 `CARDDEMO-COMMAREA`(어디서 왔는지, 사용자 타입, 선택된 계정/카드 등), 뒷부분은 이 프로그램 전용 페이징 상태 `WS-THIS-PROGCOMMAREA`(L229-248: 첫/마지막 카드 키, 현재 페이지 번호, 다음 페이지 존재 여부).
  - **화면 입력 필드**(`CCRDLIAI`, L962-978): 계정 필터 `ACCTSIDI` → `CC-ACCT-ID`, 카드 필터 `CARDSIDI` → `CC-CARD-NUM`, 7개 행 선택칸 `CRDSEL1I`~`CRDSEL7I` → `WS-EDIT-SELECT(1..7)`.
  - **눌린 AID 키**(`EIBAID`) — `CSSTRPFY` copybook이 `CCARD-AID-*` 88-level로 변환(L1416, L349 `YYYY-STORE-PFKEY`).
  - **VSAM 파일 `CARDDAT`** 의 카드 레코드들(브라우즈로 순차 읽기).

- **출력**:
  - **BMS 맵 `CCRDLIA`(맵셋 `COCRDLI`)** 화면 전송(L939-946 `EXEC CICS SEND MAP ... ERASE`). 출력 구조 `CCRDLIAO`에 제목/날짜/시간/페이지번호/7개 행 데이터/오류·정보 메시지를 채움.
  - **갱신된 commarea**(`WS-COMMAREA`, 2000바이트) — `EXEC CICS RETURN TRANSID(CCLI) COMMAREA(...)`로 다음 턴에 전달(L609-619).
  - 다음 화면으로의 제어 이전(`XCTL`) — 메뉴/상세/수정 프로그램.

## 의존성

- **COPY (카피북)**:
  - `CVCRD01Y` — 카드 화면 공용 작업영역 `CC-WORK-AREA`. AID 88-level(`CCARD-AID-ENTER` 등), `CCARD-NEXT-PROG/MAPSET/MAP`, `CCARD-ERROR-MSG`, 그리고 필터 입력값 `CC-ACCT-ID`(X(11), 숫자 뷰 `CC-ACCT-ID-N`)·`CC-CARD-NUM`(X(16), 숫자 뷰 `CC-CARD-NUM-N`).
  - `COCOM01Y` — `CARDDEMO-COMMAREA`(프로그램 간 공용 세션 상태/네비게이션).
  - `COCRDLI` — 이 화면의 BMS 심볼릭 맵(`CCRDLIAI`/`CCRDLIAO`).
  - `CVACT02Y` — 카드 레코드 `CARD-RECORD`(RECLN 150): `CARD-NUM` X(16), `CARD-ACCT-ID` 9(11), `CARD-CVV-CD` 9(3), `CARD-EMBOSSED-NAME` X(50), `CARD-EXPIRAION-DATE` X(10), `CARD-ACTIVE-STATUS` X(1), FILLER X(59).
  - `DFHBMSCA`, `DFHAID` — IBM 제공(화면 속성 상수, AID 키 상수).
  - `COTTL01Y`(화면 제목), `CSDAT01Y`(현재 날짜/시간), `CSMSG01Y`(공통 메시지), `CSUSR01Y`(로그인 사용자 데이터).
  - `CSSTRPFY` — PF키 저장 공통 로직(소스 본문에 인라인 삽입, L1416). `EIBAID`를 읽어 `CCARD-AID-*` 플래그로 변환하는 책임 (추측: copybook 내부 미열람, 사용 맥락으로 추정).
  - (주석 처리: `COCRDSL` L274, `CSMSG02Y` L283 — 현재 미사용)

- **호출 프로그램 (CALL/XCTL/LINK)**: 모두 `EXEC CICS XCTL`(제어 이전, 복귀 없음)
  - `COMEN01C` (메뉴, `LIT-MENUPGM`) — PF3 종료 시 (L402-405)
  - `COCRDSLC` (카드 상세, `LIT-CARDDTLPGM`) — 행에 `S` 입력 시 (L538-541)
  - `COCRDUPC` (카드 수정, `LIT-CARDUPDPGM`) — 행에 `U` 입력 시 (L566-569)

- **데이터셋/파일/DB 테이블**:
  - **`CARDDAT`** (`LIT-CARD-FILE VALUE 'CARDDAT '`, L213-214) — 카드 VSAM KSDS. STARTBR/READNEXT/READPREV/ENDBR로 **브라우즈(커서 페이징)**. 키는 `CARD-NUM`(카드번호 16자리). RIDFLD = `WS-CARD-RID-CARDNUM`.
  - `CARDAIX` (`LIT-CARD-FILE-ACCT-PATH VALUE 'CARDAIX '`, L215-217) — 계정번호 기준 대체 인덱스(AIX) 경로. **상수로 정의만 되어 있고 PROCEDURE DIVISION에서 실제로 참조되지 않음** (추측: 원래 계정 필터를 AIX로 처리하려던 설계 흔적이며, 실제로는 기본 키로 전체 브라우즈 후 `9500-FILTER-RECORDS`에서 메모리 필터링하는 방식으로 구현됨).

- **트랜잭션 ID 또는 EXEC PGM**:
  - 자기 트랜잭션 `CCLI` (`LIT-THISTRANID`, L181-182) — `EXEC CICS RETURN TRANSID(CCLI)`로 매 턴 재기동.
  - 연계 트랜잭션: `CM00`(메뉴), `CCDL`(상세), `CCUP`(수정) — XCTL 시 commarea의 `CCARD-NEXT-*`에 설정.

## 핵심 로직 흐름

### 1. 진입과 컨텍스트 복원 (`0000-MAIN`, L298-)
1. 작업영역 초기화 후 `WS-TRANID = 'CCLI'` 설정 (L300-307).
2. `IF EIBCALEN = 0`(최초 진입, commarea 없음) → `CARDDEMO-COMMAREA`/`WS-THIS-PROGCOMMAREA` 초기화, 사용자 타입 기본 USER, 첫 페이지로 설정 (L315-325).
3. `ELSE` → 전달된 commarea를 **두 조각으로 분해**: 앞 `LENGTH OF CARDDEMO-COMMAREA`만큼은 공용 영역으로, 그 뒤부터는 프로그램 전용 페이징 상태로 복원 (L327-331). 이것이 이 프로그램의 핵심 상태관리 기법이다.
4. 메뉴에서 막 들어온 경우(`CDEMO-PGM-ENTER` AND from-program ≠ 자기 자신) → 과거 페이징 상태를 버리고 첫 페이지부터 새로 시작 (L336-343).

### 2. PF키 정규화와 검증 (L349-414)
- `YYYY-STORE-PFKEY`(=`CSSTRPFY`)로 `EIBAID`를 `CCARD-AID-*`로 변환.
- 자기 화면에서 재진입했고 commarea가 있으면 `2000-RECEIVE-MAP` 수행 → 화면 입력 수신+검증 (L357-362).
- Enter/PF3/PF7/PF8만 유효; 그 외 키는 Enter로 강제 치환 (L370-380).
- PF3 + 자기 화면이면 즉시 `COMEN01C`로 `XCTL`(메뉴 복귀) (L384-406).

### 3. 동작 디스패치 (`EVALUATE TRUE`, L418-583)
COBOL `EVALUATE TRUE`는 Java `switch(true)`/if-else 체인과 동일. 위에서부터 첫 참 조건 실행:
- **INPUT-ERROR**: 입력 오류 → 오류 메시지 세팅, (필터 자체가 깨진 게 아니면) 목록 다시 읽고 화면 재전송 (L419-438).
- **PF7 AND 첫 페이지**: 첫 카드 키부터 전진 읽기 (L439-454). (동일 WHEN이 L439-440과 L444-445에 중복 — 위쪽은 빈 본문으로 fall-through되는 듯한 흔적; 실제 본문은 두 번째 블록. (추측) 편집 잔재.)
- **PF3 / 다른 프로그램에서 재진입**: 상태 초기화 후 첫 페이지부터 (L458-482).
- **PF8 AND 다음 페이지 존재**: 마지막 카드 키 다음부터 전진, 페이지번호 +1 (L486-497).
- **PF7 AND 첫 페이지 아님**: 첫 카드 키부터 **후진** 읽기, 페이지번호 -1 (L501-513).
- **Enter AND 행에 'S' 선택 AND 자기 화면**: 선택 행의 계정/카드번호를 commarea에 담아 `COCRDSLC`(상세)로 `XCTL` (L517-541).
- **Enter AND 행에 'U' 선택 AND 자기 화면**: 동일하게 `COCRDUPC`(수정)로 `XCTL` (L545-569).
- **WHEN OTHER**: 기본 = 첫 카드 키부터 전진 읽고 화면 전송 (L572-582).

각 분기 끝의 `GO TO COMMON-RETURN`은 공통 종료점으로 점프(Java라면 헬퍼 메서드 호출 후 `return`).

### 4. 공통 종료 (`COMMON-RETURN`, L604-620)
- 네비게이션 필드(from-tranid/program, last-map 등)를 자기 값으로 세팅.
- **두 조각을 `WS-COMMAREA`(2000)로 재직렬화**: 앞에 공용 영역, 그 뒤에 프로그램 전용 페이징 상태 (L609-612).
- `EXEC CICS RETURN TRANSID(CCLI) COMMAREA(WS-COMMAREA)` — 다음 키 입력 시 같은 트랜잭션 재기동.

### 5. 전진 읽기 (`9000-READ-FORWARD`, L1123-1262) — 페이지네이션 심장부
1. `STARTBR DATASET(CARDDAT) RIDFLD(카드번호) GTEQ` — 시작 키 **이상(>=)** 위치로 커서 설정 (L1129-1136). GTEQ = "Greater than or EQual".
2. `PERFORM UNTIL READ-LOOP-EXIT` 루프 안에서 `READNEXT`로 한 건씩 전진 (L1144-1154).
3. 각 레코드를 `9500-FILTER-RECORDS`로 필터링 (L1159-1160). 제외 대상이 아니면 화면 행 배열 `WS-ROW-*(카운터)`에 적재 (L1162-1187).
4. 첫 적재 레코드의 키를 `WS-CA-FIRST-CARDKEY`에 저장(PF7 후진용) (L1173-1184).
5. **7건(`WS-MAX-SCREEN-LINES`) 채우면** 루프 종료 직전 마지막 키 저장 후, **한 건 더 READNEXT**해서 "다음 페이지 존재 여부"(`CA-NEXT-PAGE-EXISTS`/`NOT-EXISTS`)를 판정 (L1191-1232). 이 한 건 추가 읽기가 "more pages" 화살표/메시지의 근거.
6. 7건 못 채우고 `ENDFILE`(파일 끝)이면 다음 페이지 없음 표시. 첫 페이지인데 0건이면 `WS-NO-RECORDS-FOUND` (L1233-1245).
7. `ENDBR FILE(CARDDAT)`로 브라우즈 종료 (L1258).

### 6. 후진 읽기 (`9100-READ-BACKWARDS`, L1264-1380)
- 첫 카드 키를 시작점으로 `STARTBR GTEQ` 후 `READPREV`로 역방향 읽기 (L1294-1330).
- 카운터를 7+1에서 시작해 **역순으로** 배열을 채워(아래→위) 화면상 순서를 유지 (L1284-1346). 7건 채우면(`WS-SCRN-COUNTER = 0`) 종료하며 새 첫 키 저장 (L1347-1356).
- Java 비유: `ListIterator.previous()`로 역순 순회하면서 결과 리스트를 끝에서부터 역방향으로 채워 정방향 정렬을 복원하는 것.

### 7. 레코드 필터 (`9500-FILTER-RECORDS`, L1382-1411)
- 계정 필터가 유효(`FLG-ACCTFILTER-ISVALID`)하면 `CARD-ACCT-ID = CC-ACCT-ID` 아닌 레코드 제외 (L1385-1394).
- 카드 필터가 유효하면 `CARD-NUM = CC-CARD-NUM-N` 아닌 레코드 제외 (L1396-1405).
- 즉 필터링은 DB 인덱스가 아니라 **읽은 후 메모리에서** 수행됨 (CARDAIX 미사용과 연결).

### 8. 입력 검증 (`2200-EDIT-INPUTS` 외, L985-1121)
- 계정 필터(L1003-1030): 공백이면 통과, 비숫자면 "11 DIGIT NUMBER" 오류.
- 카드 필터(L1036-1067): 공백이면 통과, 비숫자면 "16 DIGIT NUMBER" 오류.
- 선택 배열(L1073-1117): `INSPECT ... TALLYING`으로 'S'/'U' 개수를 셈. 2개 이상이면 "ONLY ONE RECORD" 오류 + 해당 행 빨강 표시. 'S'/'U'/공백 외 값은 "INVALID ACTION CODE".

> 주의 (소스 결함 가능성): L790에 단독 `I` 토큰, L755-761 1행 분기에 `CRDSEL1A OF CCRDLIAI`로 입력구조 속성에 쓰는 부분 등 일부 코드가 다른 행들과 불일치한다. 또한 L437/L453 등에서 `PERFORM 1000-SEND-MAP THRU 1000-SEND-MAP`(EXIT가 아닌 자기 단락까지)로 호출되는데, 단락 fall-through 특성상 의도와 다를 수 있다 (추측). 이식 시 정밀 검토 필요.

## Java/현대화 노트

- **commarea 직렬화 = 세션 상태**: `WS-COMMAREA`(2000B) = `CARDDEMO-COMMAREA`(공용) + `WS-THIS-PROGCOMMAREA`(전용)의 바이트 연접이다(L609-612, L327-331). 무상태 HTTP 요청 사이에 세션 토큰/`HttpSession`에 페이징 커서를 직렬화해 넘기는 것과 동형. Java에서는 두 개의 DTO 객체로 명확히 분리하되, "오프셋"이 아니라 **마지막으로 본 키(keyset)** 를 들고 다닌다는 점을 보존할 것.

- **키셋 페이지네이션(keyset/seek pagination)**: 이 프로그램은 `LIMIT/OFFSET`이 아니라 STARTBR + 마지막 키 `GTEQ`로 다음 페이지를 찾는다(L488-489 → L1131 RIDFLD). 대용량에서 OFFSET보다 효율적인 그 패턴이다. JPA/SQL 이식 시:
  ```sql
  SELECT * FROM card
  WHERE card_num >= :lastSeenCardNum
    AND (:acctId IS NULL OR card_acct_id = :acctId)
  ORDER BY card_num
  FETCH FIRST 8 ROWS ONLY   -- 7 + "다음 페이지 있나?" 판별용 1건
  ```
  마지막 1건 초과 조회로 `hasNext`를 판정하는 기법(L1197-1232)도 그대로 옮길 수 있다.

- **"한 건 더 읽기"로 hasNext 판정**: 화면을 7건 채운 뒤 8번째를 READNEXT해서 존재하면 `next page exists`(L1197-1214). Spring `Slice`/`Pageable`의 `hasNext` 구현과 동일한 트릭.

- **후진 페이징의 역순 채우기**: READPREV로 역방향 읽으며 배열을 끝→앞으로 채워 표시 순서를 정방향으로 맞춘다(L1284-1356). Java로는 역순 조회 후 `Collections.reverse()` 하거나, 처음부터 `Deque.addFirst()`로 채우면 된다.

- **REDEFINES = 같은 메모리의 두 타입 뷰** (Java에 직접 대응 없음):
  - `CC-ACCT-ID`(X(11)) ↔ `CC-ACCT-ID-N`(9(11)), `CC-CARD-NUM`(X(16)) ↔ `CC-CARD-NUM-N`(9(16)) (CVCRD01Y L34-39). 같은 바이트를 문자열로도, 숫자로도 본다. 검증 시 문자열 뷰로 공백/비숫자를 검사하고(L1017 `IS NOT NUMERIC`), 비교 시 숫자 뷰를 쓴다(L1397). Java에서는 단일 `String`으로 보관하고 `matches("\\d{11}")` + `Long.parseLong` 변환으로 표현.
  - `WS-EDIT-SELECT-FLAGS`(X(7)) ↔ `WS-EDIT-SELECT(1..7)` 배열(L72-76): 7바이트를 한 덩어리로도(INSPECT TALLYING으로 'S'/'U' 개수 세기, L1079-1082) 개별 원소로도 접근. Java라면 `char[7]` 또는 `List<SelectAction>`.
  - `WS-ALL-ROWS`(X(196)) ↔ `WS-SCREEN-ROWS OCCURS 7`(L252-260): 화면 7행을 한 번에 `LOW-VALUES`로 클리어(L1124)하고 개별 행에 데이터 적재. Java는 `CardRow[7]` 배열 + 루프로 초기화.

- **`OCCURS` = 고정 길이 배열**: 7행 화면 배열, 선택 플래그 배열 등 모두 `OCCURS 7`(1-based 인덱싱!). `I-SELECTED`는 선택된 행 번호(1~7)를 담으며 `DETAIL-WAS-REQUESTED VALUES 1 THRU 7`(L94)로 "선택 있음"을 판정. Java는 0-based 인덱스 + `Optional<Integer>` 또는 `selectedIndex == -1` 관례로 변환.

- **88-level = 명명된 불리언/열거형**: `CCARD-AID-ENTER`, `CA-NEXT-PAGE-EXISTS`, `VIEW-REQUESTED-ON`/`UPDATE-REQUESTED-ON`(L78-79) 등. 마지막 두 개는 행 선택칸이 'S'/'U'인지의 술어로, Java `enum SelectAction { VIEW, UPDATE }` + `switch`로 깔끔히 대응.

- **PIC 9(11)/9(16) 키의 자릿수 함정**: 카드번호 `CARD-NUM`은 PIC **X(16)**(문자, L5 CVACT02Y)이지만 계정 `CARD-ACCT-ID`는 PIC **9(11)**(숫자, DISPLAY 존). 16자리 카드번호·11자리 계정번호는 `int`(최대 ~21억) 범위를 넘으므로 Java에서 절대 `int`로 받지 말 것 — 카드번호는 `String`(선행 0 보존, 산술 불필요)이 정석이고, 계정번호는 `long` 또는 `String` 권장.

- **`GO TO` / 단락 fall-through**: 본 프로그램은 `GO TO COMMON-RETURN`을 디스패치 종료에 다용하고, `PERFORM A THRU B` 범위 호출도 많다. Java로 옮길 때는 각 WHEN 분기를 별도 메서드로 추출하고 명시적 `return`으로 대체하면 fall-through 위험이 사라진다.

- **CARDAIX 미사용**: 계정별 대체 인덱스(AIX)가 상수로만 존재하고 실제 I/O에 안 쓰이며, 계정 필터는 전체 브라우즈 후 메모리 필터(L1385-1394)로 처리된다. 현대화 시에는 계정번호를 WHERE 절/인덱스로 밀어넣어 **DB가 필터링**하게 바꾸는 것이 성능상 바람직하다(현재는 풀스캔에 가까움).

- **오류/정보 메시지 정책**: 한 번에 하나의 메시지만 표시하려고 `WS-ERROR-MSG-OFF`일 때만 새 메시지를 세팅하는 가드(L1056, L1111, L1218 등)를 쓴다. Java로는 검증 결과를 `List<FieldError>`로 모으되 화면엔 첫 메시지만 노출하는 식으로 표현 가능.

- **디버그용 단락**: `SEND-PLAIN-TEXT`/`SEND-LONG-TEXT`(L1422-1454)는 주석에 "프로덕션 사용 금지"로 명시된 디버깅 보조 출력. 이식 대상 아님.
