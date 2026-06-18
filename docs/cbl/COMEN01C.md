# COMEN01C — 일반 사용자 메인 메뉴

- **유형**: CICS 온라인 COBOL
- **한 줄 요약**: 일반 사용자(User 타입)가 로그인 후 가장 먼저 만나는 메인 메뉴 화면으로, COMEN02Y에 정의된 11개 옵션 테이블을 순회하며 화면을 동적으로 구성하고, 사용자가 선택한 번호에 해당하는 프로그램으로 XCTL(제어 이전)한다.

---

## 기능 설명

COMEN01C는 CardDemo 애플리케이션에서 일반 사용자 전용 메인 메뉴 역할을 한다. 로그인 프로그램 COSGN00C가 사용자 타입을 확인한 뒤 `CDEMO-USRTYP-USER`인 경우 XCTL로 이 프로그램에 진입시킨다.

프로그램은 **pseudo-conversational** 방식으로 동작한다. CICS에서 화면을 전송한 뒤 즉시 `EXEC CICS RETURN`으로 종료하고, 사용자가 키를 누르면 트랜잭션 ID `CM00`으로 다시 기동(re-entry)되어 COMMAREA를 통해 이전 상태를 복원한다. Java의 무상태 HTTP 요청/응답 사이클과 동형이다.

핵심 특징은 **데이터 주도 라우팅**이다. 어떤 화면으로 이동할지를 소스 코드 조건분기로 하드코딩하지 않고, COMEN02Y copybook이 정의하는 테이블(`CDEMO-MENU-OPT-PGMNAME`)에서 프로그램 이름을 읽어 XCTL 대상으로 사용한다. 메뉴 항목이 늘어나도 copybook만 수정하면 된다.

옵션 11 `COPAUS0C`(Pending Authorization View)는 **설치 여부 확인** 로직이 별도로 적용된다. 선택 전에 `EXEC CICS INQUIRE PROGRAM ... NOHANDLE`로 CSD(CICS System Definition)에 해당 프로그램이 등록되어 있는지 실시간으로 확인한다. 설치되어 있지 않으면 빨간 글씨로 "not installed" 메시지를 출력한다. 이는 optional 모듈을 베이스 빌드와 독립적으로 배포할 수 있게 하는 설계 패턴이다.

관리자 전용 옵션(`CDEMO-MENU-OPT-USRTYPE = 'A'`)을 일반 사용자가 선택하면 XCTL 없이 "No access - Admin Only option..." 오류 메시지를 화면에 표시한다.

---

## 입력 / 출력

- **입력**:
  - COMMAREA(`DFHCOMMAREA`): 이전 트랜잭션 턴에서 전달된 `CARDDEMO-COMMAREA` 구조체. `EIBCALEN`이 0이면 최초 진입으로 간주한다.
  - BMS 입력 맵(`COMEN1AI`): 사용자가 입력한 메뉴 선택 번호(`OPTIONI`, X(2)).
  - `EIBAID`: 사용자가 누른 키 종류(ENTER, PF3, 기타).
  - `EIBRESP`: `EXEC CICS INQUIRE PROGRAM`의 응답 코드 (옵션 11 설치 확인 시).

- **출력**:
  - BMS 출력 맵(`COMEN1AO`): 화면에 전송되는 메뉴 구조체. 헤더(제목/날짜/시간/트랜잭션명/프로그램명), 옵션 텍스트 목록(`OPTN001O`~`OPTN012O`), 오류 메시지(`ERRMSGO`).
  - COMMAREA: 다음 턴 또는 다음 프로그램으로 넘기는 `CARDDEMO-COMMAREA`. XCTL 시에는 `CDEMO-FROM-TRANID`, `CDEMO-FROM-PROGRAM`, `CDEMO-PGM-CONTEXT(=0)`을 세팅한 뒤 전달한다.
  - 제어 이전: 선택된 프로그램으로 `EXEC CICS XCTL`.

---

## 의존성

- **COPY (카피북)**:

  | Copybook | 경로 | 역할 |
  |---|---|---|
  | `COCOM01Y` | `app/cpy/COCOM01Y.cpy` | `CARDDEMO-COMMAREA` 구조 정의 (프로그램 간 공유 세션 데이터) |
  | `COMEN02Y` | `app/cpy/COMEN02Y.cpy` | 메뉴 옵션 테이블 11개 정의 (`CDEMO-MENU-OPT-COUNT`, `CDEMO-MENU-OPT-NUM/NAME/PGMNAME/USRTYPE`) |
  | `COMEN01` | `app/cpy-bms/COMEN01.CPY` | BMS 컴파일 결과 symbolic map (`COMEN1AI` 입력, `COMEN1AO` 출력, REDEFINES 관계) |
  | `COTTL01Y` | `app/cpy/COTTL01Y.cpy` | 화면 제목 리터럴 (`CCDA-TITLE01`, `CCDA-TITLE02`) |
  | `CSDAT01Y` | `app/cpy/CSDAT01Y.cpy` | `WS-DATE-TIME` 구조 (현재 날짜/시간 필드, REDEFINES 포함) |
  | `CSMSG01Y` | `app/cpy/CSMSG01Y.cpy` | 공통 메시지 리터럴 (`CCDA-MSG-INVALID-KEY`) |
  | `CSUSR01Y` | `app/cpy/CSUSR01Y.cpy` | `SEC-USER-DATA` 구조 (이 프로그램에서 직접 사용하지 않고 COPY만 선언, 참조용) |
  | `DFHAID` | CICS 시스템 제공 | 주의키 상수 (`DFHENTER`, `DFHPF3` 등) |
  | `DFHBMSCA` | CICS 시스템 제공 | 화면 속성 상수 (`DFHRED`, `DFHGREEN` 등 색상) |

- **호출 프로그램 (CALL/XCTL/LINK)**:

  | 방향 | 프로그램 | 조건 |
  |---|---|---|
  | 유입 | `COSGN00C` | 로그인 후 일반 사용자 타입일 때 XCTL로 진입 |
  | 유출 | `COSGN00C` | EIBCALEN=0 또는 PF3 키 입력 시 로그인 화면으로 복귀 |
  | 유출 | `COPAUS0C` | 옵션 11 선택 + CSD 설치 확인 통과 시 |
  | 유출 | `COACTVWC` (옵션 1) | Account View |
  | 유출 | `COACTUPC` (옵션 2) | Account Update |
  | 유출 | `COCRDLIC` (옵션 3) | Credit Card List |
  | 유출 | `COCRDSLC` (옵션 4) | Credit Card View |
  | 유출 | `COCRDUPC` (옵션 5) | Credit Card Update |
  | 유출 | `COTRN00C` (옵션 6) | Transaction List |
  | 유출 | `COTRN01C` (옵션 7) | Transaction View |
  | 유출 | `COTRN02C` (옵션 8) | Transaction Add |
  | 유출 | `CORPT00C` (옵션 9) | Transaction Reports |
  | 유출 | `COBIL00C` (옵션 10) | Bill Payment |

  모든 유출은 `EXEC CICS XCTL`(return 없는 제어 이전)이며, `CALL`이나 `EXEC CICS LINK`는 사용하지 않는다.

- **데이터셋/파일/DB 테이블**: 없음. 이 프로그램 자체는 파일이나 DB에 직접 접근하지 않는다. (COCOM01Y 주석에 언급된 `USRSEC` 파일 이름은 WS-USRSEC-FILE에 리터럴로 보관되지만 이 프로그램에서 실제 READ/WRITE하지는 않는다.)

- **트랜잭션 ID 또는 EXEC PGM**:
  - CICS 트랜잭션 ID: `CM00` (WS-TRANID, 37라인)
  - BMS 맵셋: `COMEN01` / 맵: `COMEN1A`

---

## 핵심 로직 흐름

아래는 PROCEDURE DIVISION의 단락(paragraph) 단위 흐름이다.

```
MAIN-PARA (75~110라인)
│
├─ [진입 조건] EIBCALEN = 0 (최초 진입, COMMAREA 없음)
│   └─ MOVE 'COSGN00C' TO CDEMO-FROM-PROGRAM
│      PERFORM RETURN-TO-SIGNON-SCREEN  ← 로그인 화면으로 강제 복귀
│
└─ [재진입] EIBCALEN > 0
    ├─ MOVE DFHCOMMAREA TO CARDDEMO-COMMAREA  ← 세션 복원
    │
    ├─ NOT CDEMO-PGM-REENTER (플래그 = 0, 처음 그리기)
    │   ├─ SET CDEMO-PGM-REENTER TO TRUE  ← 플래그 세팅 (다음 턴에 재진입으로 판정)
    │   ├─ MOVE LOW-VALUES TO COMEN1AO   ← 출력 맵 초기화
    │   └─ PERFORM SEND-MENU-SCREEN      ← 화면 전송
    │
    └─ CDEMO-PGM-REENTER (플래그 = 1, 사용자가 키를 눌렀음)
        ├─ PERFORM RECEIVE-MENU-SCREEN   ← 사용자 입력 수신
        └─ EVALUATE EIBAID
            ├─ DFHENTER → PERFORM PROCESS-ENTER-KEY
            ├─ DFHPF3   → PERFORM RETURN-TO-SIGNON-SCREEN
            └─ OTHER    → 오류 메시지 + PERFORM SEND-MENU-SCREEN

    [매 턴 끝] EXEC CICS RETURN TRANSID('CM00') COMMAREA(CARDDEMO-COMMAREA)
```

**PROCESS-ENTER-KEY (115~191라인) 세부 흐름**:

1. **오른쪽 공백 제거** (117~121라인): OPTIONI(X(2))를 오른쪽부터 후진 스캔하여 마지막 비공백 위치를 WS-IDX에 저장한다.
   ```cobol
   PERFORM VARYING WS-IDX
           FROM LENGTH OF OPTIONI OF COMEN1AI BY -1 UNTIL
           OPTIONI OF COMEN1AI(WS-IDX:1) NOT = SPACES OR WS-IDX = 1
   ```

2. **숫자 변환** (122~124라인): 추출한 문자열의 공백을 '0'으로 치환 후 수치형 WS-OPTION으로 이동한다.
   ```cobol
   MOVE OPTIONI(1:WS-IDX) TO WS-OPTION-X
   INSPECT WS-OPTION-X REPLACING ALL ' ' BY '0'
   MOVE WS-OPTION-X TO WS-OPTION
   ```

3. **유효성 검사** (127~134라인): WS-OPTION이 숫자가 아니거나, 0이거나, `CDEMO-MENU-OPT-COUNT`(=11)를 초과하면 오류 메시지를 출력하고 화면을 재전송한다.

4. **관리자 전용 옵션 접근 제어** (136~143라인): 사용자 타입이 `CDEMO-USRTYP-USER('U')`이고, 선택한 옵션의 `CDEMO-MENU-OPT-USRTYPE`이 `'A'`이면 ERR-FLG-ON을 세팅하고 "Admin Only" 메시지를 출력한다. (현재 COMEN02Y의 모든 옵션이 `'U'`이므로 실제로 이 분기에 걸리는 케이스는 없다. 향후 Admin 전용 옵션 추가를 위한 확장 지점.)

5. **XCTL 라우팅 EVALUATE** (145~191라인): ERR-FLG-ON이 아닐 때만 진입.
   - `WHEN CDEMO-MENU-OPT-PGMNAME(WS-OPTION) = 'COPAUS0C'` (147~168라인):
     - `EXEC CICS INQUIRE PROGRAM(...) NOHANDLE`으로 CSD에서 설치 여부 확인.
     - `EIBRESP = DFHRESP(NORMAL)`이면 XCTL 실행.
     - 아니면 빨간 글씨(`DFHRED`)로 "This option X is not installed..." 메시지 출력 후 화면 재전송.
   - `WHEN CDEMO-MENU-OPT-PGMNAME(WS-OPTION)(1:5) = 'DUMMY'` (169~176라인):
     - 초록 글씨(`DFHGREEN`)로 "This option X is coming soon..." 메시지 출력. (플레이스홀더 옵션)
   - `WHEN OTHER` (177~188라인):
     - COMMAREA에 `CDEMO-FROM-TRANID`, `CDEMO-FROM-PROGRAM`, `CDEMO-PGM-CONTEXT=0` 세팅 후 XCTL.
     - 참고: 180라인에 `MOVE WS-PGMNAME TO CDEMO-FROM-PROGRAM`이 두 번 연속 있다 (중복, 의도치 않은 코드).
   - WHEN OTHER 종료 후 `PERFORM SEND-MENU-SCREEN` 호출(190라인): XCTL이 실행되면 제어가 넘어가 이 줄은 실행되지 않는다. XCTL이 실패하거나 DUMMY 케이스인 경우에만 실행된다.

**BUILD-MENU-OPTIONS (262~303라인)**:
CDEMO-MENU-OPT-COUNT(=11) 만큼 루프를 돌며 각 옵션의 번호와 이름을 문자열로 조합한다.
```cobol
STRING CDEMO-MENU-OPT-NUM(WS-IDX)  DELIMITED BY SIZE
       '. '                         DELIMITED BY SIZE
       CDEMO-MENU-OPT-NAME(WS-IDX) DELIMITED BY SIZE
  INTO WS-MENU-OPT-TXT
```
이후 `EVALUATE WS-IDX`로 OPTN001O~OPTN012O 중 해당 필드에 복사한다. OCCURS 배열에 직접 인덱스로 쓰지 않고 `EVALUATE`로 개별 필드를 나열하는 이유는 BMS symbolic map이 고정된 개별 필드명으로 생성되기 때문이다.

---

## Java/현대화 노트

### 1. Pseudo-conversational = 무상태 HTTP 요청

매 `EXEC CICS RETURN`과 다음 트랜잭션 기동 사이에 프로그램 메모리는 완전히 사라진다. `CARDDEMO-COMMAREA`가 HTTP 세션 쿠키/토큰에 해당한다. Java로 현대화할 때 Spring MVC의 `@SessionAttributes` 또는 JWT 클레임으로 대응할 수 있다.

```java
// COBOL: EXEC CICS RETURN TRANSID('CM00') COMMAREA(CARDDEMO-COMMAREA)
// Java 대응:
@SessionAttributes("cardDemoSession")
@Controller
public class MenuController {
    @GetMapping("/menu")
    public String showMenu(@ModelAttribute("cardDemoSession") CardDemoSession session,
                           Model model) {
        buildMenuOptions(model, session);
        return "mainMenu";
    }
}
```

### 2. CDEMO-PGM-REENTER 플래그 = 단순 boolean

```cobol
10 CDEMO-PGM-CONTEXT  PIC 9(01).
   88 CDEMO-PGM-ENTER   VALUE 0.
   88 CDEMO-PGM-REENTER VALUE 1.
```
Level-88 조건명은 Java의 `boolean` 또는 `enum`에 대응한다. `CDEMO-PGM-REENTER`는 "이 화면을 이미 한 번 보낸 적 있는가"를 나타낸다. Java에서는 세션에 `boolean firstRender` 플래그로 표현한다.

### 3. 데이터 주도 라우팅 테이블 → Map<Integer, String>

COMEN02Y의 `CDEMO-MENU-OPT OCCURS 12 TIMES`는 Java의 `Map<Integer, MenuOption>` 또는 `List<MenuOption>`으로 자연스럽게 변환된다.

```java
record MenuOption(int num, String name, String programName, char userType) {}

List<MenuOption> menuOptions = List.of(
    new MenuOption(1, "Account View",     "COACTVWC", 'U'),
    new MenuOption(2, "Account Update",   "COACTUPC", 'U'),
    // ...
    new MenuOption(11, "Pending Auth View","COPAUS0C", 'U')
);
```

### 4. EXEC CICS INQUIRE PROGRAM → 서비스 레지스트리/피처 플래그

옵션 11의 설치 여부를 런타임에 CICS에 질의하는 패턴은 현대 아키텍처에서 **피처 플래그(feature flag)** 또는 **서비스 레지스트리**에 대응한다.

```java
// COBOL: EXEC CICS INQUIRE PROGRAM('COPAUS0C') NOHANDLE
// Java 대응:
if (featureFlagService.isEnabled("PENDING_AUTH_VIEW")) {
    return "redirect:/pending-auth";
} else {
    model.addAttribute("error", "This option is not installed...");
    return "mainMenu";
}
```

### 5. EXEC CICS XCTL = 302 Redirect (돌아오지 않음)

`XCTL`은 현재 프로그램을 종료하고 제어를 다른 프로그램에 넘긴다. `EXEC CICS LINK`(서브루틴 호출, 반환 있음)와 다르다. XCTL 이후의 코드(190라인 `PERFORM SEND-MENU-SCREEN`)는 XCTL이 성공하면 절대 실행되지 않는다.

```java
// COBOL: EXEC CICS XCTL PROGRAM('COACTVWC') COMMAREA(...)
// Java 대응: return "redirect:/account/view";  (302, 반환 없음)
// EXEC CICS LINK와의 차이: LINK ≈ method call (반환 있음)
```

### 6. BMS Symbolic Map의 L/F/I/O/C/P/H/V 접미사

`COMEN1AI`의 각 필드는 7개 파생 필드를 가진다(REDEFINES 포함).
- `L` (Length, S9(4) COMP): 실제 전송된 데이터 바이트 수
- `F` / `A` (Flag/Attribute): 화면 속성 (밝기, 색상, 보호 여부)
- `I` / `O` (Input/Output): 실제 데이터값

Java로 마이그레이션 시 이 구조 전체가 단순 DTO 필드로 평탄화된다. BMS의 `DFHRED`, `DFHGREEN` 같은 속성 상수는 CSS 클래스 또는 프론트엔드 스타일로 대체된다.

### 7. 주의: 중복 코드 (180라인)

```cobol
MOVE WS-PGMNAME   TO CDEMO-FROM-PROGRAM   ← 179라인
MOVE WS-PGMNAME   TO CDEMO-FROM-PROGRAM   ← 180라인 (동일 문장 중복)
```
동작에는 영향 없지만, Java 마이그레이션 시 이 중복을 제거해야 한다.

### 8. WS-OPTION-X의 RIGHT-JUSTIFIED 공백처리

```cobol
05 WS-OPTION-X  PIC X(02) JUST RIGHT.
```
`JUST RIGHT`은 값을 오른쪽 정렬로 저장하는 COBOL 한정 기능이다. 사용자가 "1" 입력 시 WS-OPTION-X는 `" 1"`이 되고, INSPECT로 공백을 '0'으로 바꾸면 `"01"`, 수치 이동 시 WS-OPTION = 1이 된다. Java에서는 `Integer.parseInt(input.strip())` 한 줄로 처리된다.

---

## 관련 파일 경로

- 소스: `/app/cbl/COMEN01C.cbl`
- BMS 맵 정의: `/app/bms/COMEN01.bms`
- BMS 컴파일 copybook: `/app/cpy-bms/COMEN01.CPY`
- 메뉴 옵션 테이블: `/app/cpy/COMEN02Y.cpy`
- COMMAREA 구조: `/app/cpy/COCOM01Y.cpy`
