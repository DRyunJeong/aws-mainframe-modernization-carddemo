# COADM01C — 관리자 메뉴 (Admin Menu)

- **유형**: CICS 온라인 COBOL
- **한 줄 요약**: 관리자(`CDEMO-USRTYP-ADMIN`) 전용 CICS 온라인 메뉴 프로그램으로, 사용자 보안 관리 및 DB2 거래 유형 관리 기능으로 전환(XCTL)하는 허브 역할을 한다.

---

## 기능 설명

COADM01C는 CardDemo 애플리케이션에서 **관리자 권한 사용자만 접근할 수 있는 메인 메뉴** 프로그램이다. 로그인 후 CICS COMMAREA에 `CDEMO-USRTYP-ADMIN('A')`이 설정된 세션만 이 화면에 도달한다.

화면(BMS 맵 `COADM1A`)에는 최대 10개의 메뉴 항목이 번호 목록으로 표시된다. 관리자가 번호를 입력하고 Enter를 누르면, COADM02Y 카피북에 테이블로 정의된 해당 옵션의 프로그램명으로 `EXEC CICS XCTL`이 수행된다. 현재 활성 옵션은 6개이며(COADM02Y 라인 2200 기준), 나머지 슬롯은 `DUMMY` 값으로 채워진다. `DUMMY` 프로그램명이 감지되면 전환 없이 "This option is not installed ..." 메시지를 표시한다.

PGMIDERR 조건 핸들러(라인 78~79)가 등록되어 있어, XCTL 대상 프로그램이 CICS CSD에 정의되지 않은 경우에도 동일한 "not installed" 메시지로 우아하게 처리된다.

---

## 입력 / 출력

- **입력**:
  - CICS COMMAREA(`CARDDEMO-COMMAREA`, 최대 32767바이트): 이전 프로그램에서 전달되는 세션 컨텍스트(사용자 ID, 사용자 유형, 프로그램 재진입 플래그 등)
  - BMS 입력 맵 `COADM1AI`: 관리자가 화면에 입력한 메뉴 번호(`OPTIONI`, PIC X(2))
  - 시스템 EIB 필드: `EIBCALEN`(COMMAREA 길이), `EIBAID`(누른 키)

- **출력**:
  - BMS 출력 맵 `COADM1AO`(ERASE 모드): 제목, 날짜/시간, 메뉴 번호 목록, 오류 메시지(`ERRMSGO`)를 포함한 전체 화면
  - 갱신된 COMMAREA: `CDEMO-FROM-TRANID`, `CDEMO-FROM-PROGRAM`, `CDEMO-PGM-CONTEXT` 등이 설정된 채 다음 EXEC CICS RETURN으로 세션에 저장됨
  - XCTL 전환: 유효한 옵션 선택 시 해당 관리 프로그램으로 제어 이전

---

## 의존성

- **COPY (카피북)**:

  | 카피북 | 경로 | 역할 |
  |---|---|---|
  | `COCOM01Y` | `app/cpy/COCOM01Y.cpy` | `CARDDEMO-COMMAREA` 정의 — 프로그램 간 공유 세션 컨텍스트 |
  | `COADM02Y` | `app/cpy/COADM02Y.cpy` | `CARDDEMO-ADMIN-MENU-OPTIONS` — 메뉴 항목 번호·이름·프로그램명 테이블 |
  | `COADM01` | `app/cpy-bms/COADM01.CPY` | BMS 심볼릭 맵 `COADM1AI`/`COADM1AO` |
  | `COTTL01Y` | `app/cpy/COTTL01Y.cpy` | 화면 타이틀 문자열(`CCDA-TITLE01`, `CCDA-TITLE02`) |
  | `CSDAT01Y` | `app/cpy/CSDAT01Y.cpy` | 날짜·시각 WORKING-STORAGE 구조체 |
  | `CSMSG01Y` | `app/cpy/CSMSG01Y.cpy` | 공통 메시지 상수(`CCDA-MSG-INVALID-KEY` 등) |
  | `CSUSR01Y` | `app/cpy/CSUSR01Y.cpy` | `SEC-USER-DATA` 레코드 레이아웃 (이 프로그램에서 직접 조회하지는 않음) |
  | `DFHAID` | CICS 시스템 | AID 상수(`DFHENTER`, `DFHPF3` 등) |
  | `DFHBMSCA` | CICS 시스템 | BMS 색상·속성 상수(`DFHGREEN` 등) |

- **호출 프로그램 (CALL/XCTL/LINK)**:

  | 방향 | 프로그램명 | 조건 |
  |---|---|---|
  | XCTL → | `COSGN00C` (로그인 화면) | EIBCALEN=0 최초 진입 또는 PF3 키 |
  | XCTL → | `COUSR00C` (사용자 목록) | 옵션 1 선택 |
  | XCTL → | `COUSR01C` (사용자 추가) | 옵션 2 선택 |
  | XCTL → | `COUSR02C` (사용자 수정) | 옵션 3 선택 |
  | XCTL → | `COUSR03C` (사용자 삭제) | 옵션 4 선택 |
  | XCTL → | `COTRTLIC` (거래유형 목록/수정, DB2) | 옵션 5 선택 |
  | XCTL → | `COTRTUPC` (거래유형 유지보수, DB2) | 옵션 6 선택 |

- **데이터셋/파일/DB 테이블**:
  - 이 프로그램 자체는 VSAM 파일이나 DB2 테이블에 직접 접근하지 않는다. `WS-USRSEC-FILE PIC X(08) VALUE 'USRSEC  '`(라인 39)가 WORKING-STORAGE에 정의되어 있으나, 이 프로그램 내에서 실제 파일 OPEN/READ에는 사용되지 않는다.

- **트랜잭션 ID 또는 EXEC PGM**:
  - 트랜잭션 ID: `CA00` (`WS-TRANID`, 라인 37)
  - `EXEC CICS RETURN TRANSID(CA00) COMMAREA(...)` — 화면 대기 후 이 프로그램으로 재진입한다(라인 111~114).

---

## 핵심 로직 흐름

```
MAIN-PARA (진입점)
│
├─[EIBCALEN = 0] ──► RETURN-TO-SIGNON-SCREEN
│                      XCTL → COSGN00C (비정상 진입, 로그인 화면으로 강제 이동)
│
└─[EIBCALEN > 0]
    │  MOVE DFHCOMMAREA → CARDDEMO-COMMAREA  ← COMMAREA 복원
    │
    ├─[NOT CDEMO-PGM-REENTER]  ← 첫 번째 진입 (값 = 0)
    │   SET CDEMO-PGM-REENTER TO TRUE         ← 플래그를 1로 변경
    │   PERFORM SEND-MENU-SCREEN              ← 메뉴 초기 표시
    │   EXEC CICS RETURN TRANSID(CA00)        ← 입력 대기
    │
    └─[CDEMO-PGM-REENTER]      ← 재진입 (사용자가 입력 후 CA00 트랜잭션 기동)
        PERFORM RECEIVE-MENU-SCREEN           ← 화면 데이터 수신
        │
        EVALUATE EIBAID
        ├─ DFHENTER ──► PROCESS-ENTER-KEY
        │               │
        │               │ 1. OPTIONI 우측에서 공백 제거 후 MOVE → WS-OPTION-X
        │               │ 2. INSPECT WS-OPTION-X: 공백 → '0' 치환
        │               │ 3. WS-OPTION-X → WS-OPTION (숫자 변환)
        │               │ 4. 검증: 비숫자 OR > CDEMO-ADMIN-OPT-COUNT(6) OR = 0
        │               │         → ERR-FLG-ON, SEND-MENU-SCREEN (오류 표시)
        │               │
        │               └─ [오류 없음]
        │                   IF CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION)(1:5) ≠ 'DUMMY'
        │                       EXEC CICS XCTL PROGRAM(...)  ← 해당 관리 프로그램으로 전환
        │                   ELSE
        │                       "This option is not installed ..." 메시지
        │                       PERFORM SEND-MENU-SCREEN
        │
        ├─ DFHPF3  ──► RETURN-TO-SIGNON-SCREEN
        │               XCTL → COSGN00C
        │
        └─ OTHER   ──► 잘못된 키 오류 메시지 + SEND-MENU-SCREEN
```

**SEND-MENU-SCREEN** 흐름:
1. `POPULATE-HEADER-INFO`: `FUNCTION CURRENT-DATE` → 날짜·시각 포맷팅 → 맵 헤더 필드 설정
2. `BUILD-MENU-OPTIONS`: `PERFORM VARYING WS-IDX 1 → CDEMO-ADMIN-OPT-COUNT(6)` 루프로 COADM02Y 테이블에서 메뉴 텍스트를 조합(`STRING`)하여 `OPTN001O`~`OPTN006O`에 배치 (7~10번 슬롯은 CONTINUE, 즉 공백 유지)
3. `EXEC CICS SEND MAP('COADM1A') ERASE`: 화면 전체를 새로 그림

**PGMIDERR-ERR-PARA**: `EXEC CICS HANDLE CONDITION PGMIDERR`로 등록(라인 78~79). XCTL 대상 프로그램이 CICS CSD에 없으면 이 단락으로 점프 → "not installed" 메시지 → SEND-MENU-SCREEN → EXEC CICS RETURN.

---

## Java/현대화 노트

### 1. Pseudo-conversational 패턴 → HTTP stateless 요청

COBOL 측에서 `CDEMO-PGM-REENTER`(PIC 9(01), 0/1) 플래그가 "첫 방문 vs 재진입"을 구분하는 이유는 CICS 트랜잭션이 매 화면 송신 후 종료되고 다음 사용자 입력 시 새로운 트랜잭션으로 재시작되기 때문이다. COMMAREA가 HTTP 쿠키/세션 토큰에 해당한다.

```java
// Java Spring MVC 등가 패턴
@GetMapping("/admin/menu")
public String showAdminMenu(HttpSession session, Model model) {
    // EIBCALEN=0 처리 — 세션 없으면 로그인으로 리다이렉트
    if (session.getAttribute("cardDemoSession") == null) {
        return "redirect:/signon";
    }
    model.addAttribute("menuOptions", adminMenuService.getOptions());
    return "admin/menu";  // SEND-MENU-SCREEN
}

@PostMapping("/admin/menu")
public String processAdminMenu(@RequestParam String option, HttpSession session) {
    int optNum = Integer.parseInt(option.strip());
    if (optNum < 1 || optNum > MAX_OPTION) {
        // ERR-FLG-ON → WS-MESSAGE
        model.addAttribute("errorMsg", "Please enter a valid option number...");
        return "admin/menu";
    }
    return "redirect:" + adminMenuService.getTargetUrl(optNum);  // XCTL
}
```

### 2. COADM02Y 메뉴 테이블 → 데이터 주도 설계

COADM02Y의 `CDEMO-ADMIN-OPTIONS-DATA`는 순수 데이터(번호 + 이름 + 프로그램명)를 평탄한 바이트 배열로 정의하고, `CDEMO-ADMIN-OPTIONS REDEFINES` 구문으로 9-occurrence 배열로 재해석한다. Java에서는 `List<AdminMenuOption>` 또는 enum으로 표현한다.

```java
public enum AdminMenuOption {
    USER_LIST    (1, "User List (Security)",              "COUSR00C"),
    USER_ADD     (2, "User Add (Security)",               "COUSR01C"),
    USER_UPDATE  (3, "User Update (Security)",            "COUSR02C"),
    USER_DELETE  (4, "User Delete (Security)",            "COUSR03C"),
    TXN_TYPE_LIST(5, "Transaction Type List/Update (Db2)","COTRTLIC"),
    TXN_TYPE_MAINT(6,"Transaction Type Maintenance (Db2)","COTRTUPC");
    // 7~9: 미설치 슬롯 (DUMMY)
    ...
}
```

### 3. REDEFINES 주의

`COADM1AO REDEFINES COADM1AI`(COADM01.CPY 라인 139): 입력 맵과 출력 맵이 **동일 메모리 공간을 공유**한다. 입력 필드명은 `...I` 접미사, 출력 필드명은 `...O` 접미사이며, 색상·속성 제어 바이트(`...C`, `...P`, `...H`, `...V`)가 출력 측에만 존재한다. Java에는 직접 대응 개념이 없다 — DTO를 입력/출력 별도 클래스로 분리하는 것이 자연스럽다.

### 4. DUMMY 프로그램명 체크 vs PGMIDERR 핸들러 이중 방어

라인 141의 `(1:5) NOT = 'DUMMY'` 검사는 소프트웨어 수준 방어이고, 라인 78~79의 `HANDLE CONDITION PGMIDERR`는 CICS 런타임 수준 방어다. 두 계층이 공존하여 "설치되지 않은 옵션"을 안전하게 처리한다. Java 마이그레이션 시 `PGMIDERR` 핸들러 로직을 누락하지 않도록 주의한다 — Spring의 `@ExceptionHandler`나 `try-catch`로 대응해야 한다.

### 5. STRING 문 → String.format / StringBuilder

```cobol
STRING CDEMO-ADMIN-OPT-NUM(WS-IDX)  DELIMITED BY SIZE
       '. '                         DELIMITED BY SIZE
       CDEMO-ADMIN-OPT-NAME(WS-IDX) DELIMITED BY SIZE
  INTO WS-ADMIN-OPT-TXT
```
`DELIMITED BY SIZE`는 전체 필드 길이만큼 복사(우측 공백 포함). Java에서는 `String.format("%02d. %-35s", num, name)` 또는 `String.strip()`으로 우측 공백을 제거하는 방식을 선택해야 한다.

### 6. 옵션 입력 공백 처리 — INSPECT 패턴

```cobol
INSPECT WS-OPTION-X REPLACING ALL ' ' BY '0'
MOVE WS-OPTION-X TO WS-OPTION
```
사용자가 한 자리 숫자(예: `'5 '`)를 입력했을 때 우측 공백을 `'0'`으로 치환하여 2자리 숫자(`'50'`)처럼 만드는 것이 아니라, `JUST RIGHT`(라인 45) 특성에 의해 이미 우측 정렬되어 있으므로 왼쪽 공백이 `'0'`으로 채워진다(`' 5'` → `'05'`). Java에서는 `Integer.parseInt(option.strip())` 또는 `option.trim()`으로 동일하게 처리한다.

### 7. WS-USRSEC-FILE 미사용 선언

`WS-USRSEC-FILE PIC X(08) VALUE 'USRSEC  '`(라인 39)는 WORKING-STORAGE에 선언되어 있으나 이 프로그램의 PROCEDURE DIVISION에서 실제로 사용되지 않는다. 사용자 파일 I/O는 COUSR0xC 계열 프로그램에서 수행된다. Java 마이그레이션 시 이 필드는 제거해도 무방하다.
