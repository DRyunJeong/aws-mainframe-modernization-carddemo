# COUSR00C — 사용자 목록 조회 (페이지네이션)

- **유형**: CICS 온라인 COBOL
- **한 줄 요약**: USRSEC VSAM 파일에서 사용자 레코드를 한 페이지에 10건씩 표시하고, PF7(이전)/PF8(다음) 키로 양방향 페이지네이션을 제공하는 관리자용 사용자 목록 화면이다.

---

## 기능 설명

COUSR00C는 CardDemo 관리자 메뉴(COADM01C)에서 진입하는 사용자 관리 목록 화면이다. USRSEC VSAM KSDS 파일을 CICS browse(STARTBR / READNEXT / READPREV / ENDBR) 방식으로 탐색하여 최대 10건의 사용자 정보(ID, 성, 이름, 유형)를 3270 터미널 화면에 출력한다.

각 행에는 선택 입력란(SEL)이 있으며, 사용자가 `U`를 입력하면 해당 사용자의 수정 화면(COUSR02C)으로, `D`를 입력하면 삭제 화면(COUSR03C)으로 XCTL 이동한다. 또한 화면 상단의 USRIDIN 입력란에 사용자 ID를 직접 입력하면 해당 ID를 시작 키로 검색을 초기화할 수 있다.

페이지네이션 상태(첫 번째 사용자 ID, 마지막 사용자 ID, 현재 페이지 번호, 다음 페이지 존재 여부)는 COMMAREA(`CDEMO-CU00-INFO`)에 저장되어 pseudo-conversational 라운드트립 사이에 유지된다.

---

## 입력 / 출력

- **입력**:
  - COMMAREA(`CARDDEMO-COMMAREA`, COCOM01Y) — 이전 화면에서 전달된 내비게이션 컨텍스트 및 페이지 상태 (`CDEMO-CU00-INFO`)
  - BMS 맵 수신(`COUSR0AI`, COUSR00.CPY) — 사용자 키 입력: USRIDINI(검색 시작 ID), SEL0001I~SEL0010I(행 선택자)
  - EIBAID — 누른 기능키 (DFHENTER / DFHPF3 / DFHPF7 / DFHPF8)
  - USRSEC VSAM KSDS — 사용자 보안 레코드(SEC-USER-DATA, CSUSR01Y)

- **출력**:
  - BMS 맵 전송(`COUSR0AO`, COUSR00.CPY) — 10행의 사용자 목록(USRID01O~USRID10O, FNAME01O~FNAME10O, LNAME01O~LNAME10O, UTYPE01O~UTYPE10O), 페이지 번호(PAGENUMO), 에러 메시지(ERRMSGO)
  - COMMAREA — 갱신된 페이지 상태를 CICS RETURN 시 다음 턴으로 전달
  - XCTL → COUSR02C 또는 COUSR03C (사용자 선택 시)

---

## 의존성

- **COPY (카피북)**:

  | 카피북 | 경로 | 역할 |
  |--------|------|------|
  | `COCOM01Y` | app/cpy/COCOM01Y.cpy | 전체 COMMAREA 레이아웃(`CARDDEMO-COMMAREA`). `CDEMO-CU00-INFO`(페이지 상태)도 이 안에 인라인 정의됨 (라인 67~75) |
  | `COUSR00` | app/cpy-bms/COUSR00.CPY | BMS 심볼릭 맵 `COUSR0AI`(입력) / `COUSR0AO`(REDEFINES 출력). 10행 × (SEL·USRID·FNAME·LNAME·UTYPE) + 헤더 필드 |
  | `CSUSR01Y` | app/cpy/CSUSR01Y.cpy | USRSEC 파일 레코드 레이아웃 `SEC-USER-DATA` |
  | `COTTL01Y` | app/cpy/COTTL01Y.cpy | 화면 타이틀 상수 (`CCDA-TITLE01`, `CCDA-TITLE02`) |
  | `CSDAT01Y` | app/cpy/CSDAT01Y.cpy | 현재 날짜/시간 조작용 워킹 스토리지 구조 |
  | `CSMSG01Y` | app/cpy/CSMSG01Y.cpy | 공통 메시지 상수 (`CCDA-MSG-INVALID-KEY` 등) |
  | `DFHAID` | IBM 시스템 | EIBAID 기능키 상수 (DFHENTER, DFHPF3, DFHPF7, DFHPF8) |
  | `DFHBMSCA` | IBM 시스템 | BMS 화면 속성 바이트 상수 |

- **호출 프로그램 (CALL/XCTL/LINK)**:

  | 이동 방식 | 대상 프로그램 | 조건 |
  |-----------|--------------|------|
  | `EXEC CICS XCTL` | `COUSR02C` | 선택자 `U` 또는 `u` 입력 — 사용자 수정 화면 (라인 197) |
  | `EXEC CICS XCTL` | `COUSR03C` | 선택자 `D` 또는 `d` 입력 — 사용자 삭제 화면 (라인 207) |
  | `EXEC CICS XCTL` | `COADM01C` | PF3 — 관리자 메뉴로 복귀 (라인 126) |
  | `EXEC CICS XCTL` | `COSGN00C` | EIBCALEN=0 (비정상 직접 진입) 또는 CDEMO-TO-PROGRAM이 공백일 때 (라인 111, 509) |

- **데이터셋/파일/DB 테이블**:
  - `USRSEC` — VSAM KSDS, 사용자 보안 레코드. 키: `SEC-USR-ID`(PIC X(8)). CICS browse 전용(STARTBR/READNEXT/READPREV/ENDBR). 쓰기 없음.

- **트랜잭션 ID 또는 EXEC PGM**:
  - 트랜잭션 ID: `CU00` (라인 37, `WS-TRANID`)

---

## 핵심 로직 흐름

### 진입 분기 (MAIN-PARA, 라인 98~144)

```
EIBCALEN = 0?
  YES → XCTL to COSGN00C  (직접 트랜잭션 입력 방어)
  NO  → COMMAREA를 CARDDEMO-COMMAREA로 복사
        CDEMO-PGM-REENTER?
          NO  → 최초 진입: CDEMO-PGM-REENTER=TRUE 설정
                           PROCESS-ENTER-KEY
                           SEND-USRLST-SCREEN
          YES → RECEIVE-USRLST-SCREEN
                EVALUATE EIBAID
                  DFHENTER → PROCESS-ENTER-KEY
                  DFHPF3   → XCTL COADM01C
                  DFHPF7   → PROCESS-PF7-KEY  (이전 페이지)
                  DFHPF8   → PROCESS-PF8-KEY  (다음 페이지)
                  OTHER    → 오류 메시지 + SEND-USRLST-SCREEN
EXEC CICS RETURN TRANSID('CU00') COMMAREA(...)
```

pseudo-conversational 패턴: RETURN 이후 이 프로그램의 메모리는 완전히 사라진다. 다음 키 입력 시 트랜잭션 `CU00`이 새로 기동되고 COMMAREA에서 페이지 상태를 복원한다.

---

### PROCESS-ENTER-KEY (라인 149~232)

1. **선택자 스캔**: `SEL0001I`~`SEL0010I` 10개를 EVALUATE TRUE로 순서대로 검사. 첫 번째 비공백·비저위값 항목의 선택 문자를 `CDEMO-CU00-USR-SEL-FLG`, 해당 사용자 ID를 `CDEMO-CU00-USR-SELECTED`에 저장.
2. **액션 분기**: 선택이 있으면 `CDEMO-CU00-USR-SEL-FLG` 값으로 EVALUATE:
   - `U`/`u` → XCTL COUSR02C (수정)
   - `D`/`d` → XCTL COUSR03C (삭제)
   - 그 외 → "Invalid selection" 메시지
3. **검색 키 설정**: `USRIDINI`(화면 입력 ID)가 있으면 `SEC-USR-ID`에 복사; 공백이면 LOW-VALUES(파일 맨 처음부터 browse 시작).
4. **페이지 초기화**: `CDEMO-CU00-PAGE-NUM = 0` 후 `PROCESS-PAGE-FORWARD` 호출.

---

### PROCESS-PF7-KEY — 이전 페이지 (라인 237~255)

- 현재 첫 번째 사용자 ID(`CDEMO-CU00-USRID-FIRST`)를 `SEC-USR-ID`에 설정.
- `NEXT-PAGE-YES` 플래그 설정(현재 페이지에서는 다음 페이지가 있음을 표시).
- `CDEMO-CU00-PAGE-NUM > 1`이면 `PROCESS-PAGE-BACKWARD` 호출; 이미 1페이지면 "top of page" 메시지.

---

### PROCESS-PF8-KEY — 다음 페이지 (라인 260~277)

- 현재 마지막 사용자 ID(`CDEMO-CU00-USRID-LAST`)를 `SEC-USR-ID`에 설정.
- `NEXT-PAGE-YES` 플래그가 세워져 있을 때만 `PROCESS-PAGE-FORWARD` 호출; 아니면 "bottom of page" 메시지.

---

### PROCESS-PAGE-FORWARD (라인 282~331)

```
STARTBR USRSEC (키: SEC-USR-ID)
IF NOT ERR-FLG-ON
  IF EIBAID ≠ ENTER AND PF7 AND PF3
      READNEXT (최초 키 레코드 자체를 건너뜀 — 시작 키 이후부터 읽기)
  INITIALIZE 10개 행 공백으로 초기화
  WS-IDX = 1
  PERFORM UNTIL WS-IDX >= 11 OR EOF OR ERR
      READNEXT → POPULATE-USER-DATA(WS-IDX)
      WS-IDX + 1
  END-PERFORM
  IF NOT EOF                    ← 10건 채운 뒤 레코드가 더 있는지 확인
      PAGE-NUM + 1
      READNEXT 한 번 더 시도
      레코드 있으면 NEXT-PAGE-YES, 없으면 NEXT-PAGE-NO
  ELSE
      NEXT-PAGE-NO
      WS-IDX > 1이면 PAGE-NUM + 1
  END-IF
  ENDBR
  SEND-USRLST-SCREEN
END-IF
```

**핵심 설계**: 10건을 채운 후 추가 READNEXT를 한 번 더 수행해 "다음 페이지 존재 여부"를 미리 탐지한다(`NEXT-PAGE-YES/NO`). 이 값이 COMMAREA에 저장되어 PF8 처리 시 guard 조건으로 쓰인다.

---

### PROCESS-PAGE-BACKWARD (라인 336~379)

```
STARTBR USRSEC (키: CDEMO-CU00-USRID-FIRST)
IF NOT ERR-FLG-ON
  IF EIBAID ≠ ENTER AND PF8
      READPREV (현재 첫 번째 레코드 자체 건너뜀)
  INITIALIZE 10개 행 공백
  WS-IDX = 10
  PERFORM UNTIL WS-IDX <= 0 OR EOF OR ERR
      READPREV → POPULATE-USER-DATA(WS-IDX)  ← 역방향으로 채움
      WS-IDX - 1
  END-PERFORM
  (이전 페이지가 더 있는지 READPREV로 탐지 후 PAGE-NUM 감소)
  ENDBR
  SEND-USRLST-SCREEN
END-IF
```

역방향 browse(READPREV)로 10건을 WS-IDX=10부터 1 방향으로 채우므로 화면에는 오름차순 순서로 표시된다.

---

### POPULATE-USER-DATA / INITIALIZE-USER-DATA (라인 384~501)

WS-IDX(1~10) 값으로 EVALUATE하여 해당 행의 BMS 맵 필드(`USRID01I`~`USRID10I` 등)에 VSAM 레코드 필드를 복사하거나 공백으로 초기화한다. COBOL에서 배열로 처리 가능한 부분(`OCCURS 10 TIMES` 테이블 `WS-USER-DATA`가 선언되어 있음에도 불구하고)을 EVALUATE로 1~10을 하드코딩한 이유는 BMS 심볼릭 맵 필드명이 순번 접미사(`01`~`10`) 형태이기 때문이다. 직접 인덱스로 접근하는 Java와 달리 BMS 맵 구조는 배열이 아닌 고정명 필드의 나열이다.

---

### 파일 I/O 단락들 (라인 586~691)

| 단락 | CICS 명령 | 역할 |
|------|-----------|------|
| `STARTBR-USER-SEC-FILE` | `EXEC CICS STARTBR DATASET RIDFLD KEYLENGTH` | browse 커서 위치 설정 |
| `READNEXT-USER-SEC-FILE` | `EXEC CICS READNEXT DATASET INTO RIDFLD` | 다음 레코드 읽기 |
| `READPREV-USER-SEC-FILE` | `EXEC CICS READPREV DATASET INTO RIDFLD` | 이전 레코드 읽기 |
| `ENDBR-USER-SEC-FILE` | `EXEC CICS ENDBR DATASET` | browse 세션 종료 |

DFHRESP(NOTFND) — 시작 키가 파일에 없어도 정상 처리(암묵적 GTEQ 동작, 라인 592 주석 참조).
DFHRESP(ENDFILE) — 파일 끝/처음 도달 시 `USER-SEC-EOF` 플래그를 세우고 메시지를 표시.
OTHER — `WS-ERR-FLG = 'Y'`를 세워 이후 로직 전체를 건너뛴다.

---

## Java/현대화 노트

### 1. Pseudo-conversational → HTTP Stateless 패턴

```java
// COBOL: EXEC CICS RETURN TRANSID COMMAREA
// → Spring MVC Controller 메서드가 매 요청마다 새로 생성되고
//    세션 또는 URL 파라미터로 페이지 상태를 유지하는 구조와 동일

@GetMapping("/users")
public String listUsers(
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(required = false) String startUserId,
    Model model, HttpSession session) { ... }
```

`CARDDEMO-COMMAREA`의 `CDEMO-CU00-INFO` 구조체는 아래 Java DTO에 직접 대응한다:

```java
public class UserListPageState {
    private String firstUserId;   // CDEMO-CU00-USRID-FIRST  PIC X(8)
    private String lastUserId;    // CDEMO-CU00-USRID-LAST   PIC X(8)
    private int pageNum;          // CDEMO-CU00-PAGE-NUM     PIC 9(8)
    private boolean hasNextPage;  // CDEMO-CU00-NEXT-PAGE-FLG  '88 NEXT-PAGE-YES'
    private String selFlag;       // CDEMO-CU00-USR-SEL-FLG  PIC X(1)
    private String selectedUserId;// CDEMO-CU00-USR-SELECTED PIC X(8)
}
```

### 2. VSAM browse 페이지네이션 → JPA/Keyset 페이지네이션

COBOL의 STARTBR+READNEXT/READPREV는 keyset(cursor) 페이지네이션과 동일한 원리다. offset 기반(`LIMIT/OFFSET`)이 아니라 마지막으로 본 키 이후부터 읽는다.

```java
// PROCESS-PAGE-FORWARD 동등 코드
List<UserRecord> fetchNextPage(String startKey, int pageSize) {
    return userRepository
        .findByUserIdGreaterThanEqualOrderByUserId(startKey)
        .stream().limit(pageSize + 1)  // +1로 다음 페이지 존재 확인
        .collect(Collectors.toList());
}
```

역방향(READPREV) 조회는 COBOL에서 간단하지만 JPA에서는 `findByUserIdLessThanOrderByUserIdDesc(...).limit(10).reversed()` 형태로 처리해야 한다.

### 3. EVALUATE TRUE (다중 선택자 스캔) → Stream

```java
// PROCESS-ENTER-KEY의 SEL0001I~SEL0010I 스캔
IntStream.rangeClosed(1, 10)
    .filter(i -> !row.get(i).getSelFlag().isBlank())
    .findFirst()
    .ifPresent(i -> handleSelection(row.get(i)));
```

COBOL은 배열 인덱스 접근 대신 EVALUATE로 10개를 개별 분기한다. 이는 BMS 맵 필드가 배열이 아닌 고정명 필드이기 때문이며, Java에서는 List 또는 배열로 통합해 처리하면 코드량이 대폭 줄어든다.

### 4. BMS 심볼릭 맵의 REDEFINES

`COUSR0AO REDEFINES COUSR0AI`는 동일한 메모리 영역을 입력용 뷰와 출력용 뷰로 공유하는 C 언어의 `union`과 같다. Java에는 직접 대응이 없다. 현대화 시에는 별도의 입력 DTO / 출력 DTO로 분리하고 명시적으로 매핑한다.

각 필드의 `L`(Length), `F`(Flag/Attribute), `A`(Attribute REDEFINES), `I/O`(실제 데이터) 4-tuple 구조는 IBM BMS의 심볼릭 맵 생성 컨벤션이다. Java에서는 단순한 `String` 필드로 변환하고 화면 속성(색상, 보호) 제어는 프론트엔드 레이어로 이전한다.

### 5. PIC 클로즈 → Java 타입 대응

| COBOL 필드 | PIC | Java 타입 | 비고 |
|------------|-----|-----------|------|
| `SEC-USR-ID` | `PIC X(8)` | `String` (max 8) | VSAM 키, 공백 패딩 유의 |
| `SEC-USR-FNAME` | `PIC X(20)` | `String` (max 20) | |
| `SEC-USR-LNAME` | `PIC X(20)` | `String` (max 20) | |
| `SEC-USR-PWD` | `PIC X(8)` | `String` — 단, 평문 저장 | 현대화 시 BCrypt 등으로 교체 필요 |
| `SEC-USR-TYPE` | `PIC X(1)` | `enum UserType { ADMIN('A'), USER('U') }` | |
| `CDEMO-CU00-PAGE-NUM` | `PIC 9(8)` | `int` | DISPLAY 형식, 8자리 양의 정수 |
| `WS-RESP-CD` | `PIC S9(9) COMP` | `int` | IBM binary fullword |

### 6. 주요 현대화 주의사항

- **평문 비밀번호**: `SEC-USR-PWD(PIC X(8))`는 VSAM에 평문으로 저장된다. 현대화 시 반드시 해시 처리로 교체해야 한다.
- **EBCDIC 정렬**: VSAM 키 정렬은 EBCDIC 코드 순서를 따른다. ASCII/UTF-8 환경으로 데이터를 마이그레이션할 때 문자 순서가 달라질 수 있으며, 특히 소문자·특수문자 범위에서 차이가 발생한다.
- **LOW-VALUES/HIGH-VALUES**: `SEC-USR-ID = LOW-VALUES`는 파일 처음부터 탐색하는 관용 표현이다. Java에서는 `null` 또는 빈 문자열 조건으로 대체하고, Repository 메서드 시그니처를 명확히 분리한다.
- **STARTBR NOTFND 처리**: 라인 592의 `GTEQ` 주석은 이 STARTBR이 정확한 키 매치가 아니라 "같거나 큰 첫 번째 키"부터 시작함을 의미한다(추측: 실제 컴파일 결과는 IBM 기본값인 GTEQ이 적용된다고 가정). SQL에서는 `WHERE user_id >= :startKey ORDER BY user_id` 에 해당한다.
- **SEND ERASE vs 미포함**: `WS-SEND-ERASE-FLG`로 `ERASE` 옵션을 제어한다(라인 528~543). 페이지 경계 메시지(top/bottom) 표시 시에는 ERASE 없이 전송하여 기존 화면 내용을 보존한다.

---

*소스 버전: CardDemo_v1.0-15-g27d6c6f-68 (2022-07-19)*
