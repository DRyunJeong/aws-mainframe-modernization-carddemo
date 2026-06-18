# COSGN00C — 사용자 로그인 화면 (CardDemo Signon)

- **유형**: CICS 온라인 COBOL (Pseudo-conversational)
- **한 줄 요약**: CardDemo 애플리케이션의 진입 관문으로, 사용자 ID와 비밀번호를 입력받아 USRSEC VSAM 파일로 인증하고, 사용자 유형(관리자/일반)에 따라 각각의 메인 메뉴 프로그램으로 제어를 이전한다.

---

## 기능 설명

COSGN00C는 CardDemo의 **유일한 로그인 진입점**이다. 트랜잭션 ID `CC00`로 기동되며, 3270 단말기 화면(BMS 맵 `COSGN0A`)에 사용자 ID와 비밀번호 입력 필드를 표시한다.

처리 흐름은 전형적인 **pseudo-conversational** 패턴이다. CICS는 COBOL 프로그램을 한 번 실행하고 메모리에서 완전히 제거한다(`EXEC CICS RETURN`). 사용자가 키를 누르면 같은 트랜잭션 ID(`CC00`)로 프로그램을 다시 기동하며, 이전 상태는 COMMAREA(`CARDDEMO-COMMAREA`)를 통해 전달된다. Java 웹 애플리케이션의 "무상태 HTTP 요청 + 세션 쿠키" 구조와 동형이다.

인증 성공 시 프로그램은 `EXEC CICS XCTL`을 사용해 제어를 **완전히 이전**하고 스스로 종료된다(Java의 `response.sendRedirect()`와 유사, 단 돌아오지 않음):

- 관리자 (`SEC-USR-TYPE = 'A'`) → `COADM01C` (관리자 메인 메뉴)
- 일반 사용자 (`SEC-USR-TYPE = 'U'`) → `COMEN01C` (일반 사용자 메인 메뉴)

PF3 키 입력 시 "Thank you" 메시지를 출력하고 `EXEC CICS RETURN`(TRANSID 없음)으로 세션을 완전히 종료한다.

비밀번호 비교는 USRSEC 파일에서 읽은 `SEC-USR-PWD`와 화면 입력값을 **직접 문자열 비교**한다(라인 223). 해싱 없이 평문 비교임에 유의해야 한다.

---

## 입력 / 출력

**입력:**

| 소스 | 필드 | 설명 |
|------|------|------|
| BMS 맵 입력 `COSGN0AI.USERIDI` | `PIC X(8)` | 사용자가 입력한 사용자 ID |
| BMS 맵 입력 `COSGN0AI.PASSWDI` | `PIC X(8)` | 사용자가 입력한 비밀번호 |
| VSAM KSDS `USRSEC` | `SEC-USER-DATA` | 사용자 보안 레코드 (ID 키로 랜덤 읽기) |
| CICS EIB | `EIBCALEN`, `EIBAID` | 최초 진입 여부 / 입력 키 종류 판별 |
| COMMAREA | `CARDDEMO-COMMAREA` | 프로그램 간 세션 상태 (재진입 시) |

**출력:**

| 대상 | 필드 | 설명 |
|------|------|------|
| BMS 맵 출력 `COSGN0AO` | 전체 화면 | 로그인 화면 (제목/날짜/시간/오류메시지 포함) |
| COMMAREA | `CDEMO-USER-ID`, `CDEMO-USER-TYPE`, `CDEMO-FROM-TRANID`, `CDEMO-FROM-PROGRAM`, `CDEMO-PGM-CONTEXT` | 인증 성공 후 다음 프로그램으로 전달되는 세션 정보 |
| CICS XCTL | `COADM01C` 또는 `COMEN01C` | 인증 성공 시 다음 화면으로 제어 이전 |

---

## 의존성

**COPY (카피북):**

| 카피북 | 경로 | 역할 |
|--------|------|------|
| `COCOM01Y` | `app/cpy/COCOM01Y.cpy` | `CARDDEMO-COMMAREA` 정의 — 모든 CO* 프로그램의 세션 객체 |
| `COSGN00` | `app/cpy-bms/COSGN00.CPY` | BMS symbolic map — `COSGN0AI`(입력) / `COSGN0AO`(출력, REDEFINES) |
| `COTTL01Y` | `app/cpy/COTTL01Y.cpy` | `CCDA-TITLE01`/`CCDA-TITLE02`/`CCDA-THANK-YOU` 화면 제목 리터럴 |
| `CSDAT01Y` | `app/cpy/CSDAT01Y.cpy` | `WS-DATE-TIME` — 현재 날짜/시간 포맷 구조체 |
| `CSMSG01Y` | `app/cpy/CSMSG01Y.cpy` | `CCDA-MSG-THANK-YOU`, `CCDA-MSG-INVALID-KEY` 공통 메시지 |
| `CSUSR01Y` | `app/cpy/CSUSR01Y.cpy` | `SEC-USER-DATA` — USRSEC 파일 레코드 레이아웃 |
| `DFHAID` | CICS 시스템 | `DFHENTER`, `DFHPF3` 등 EIBAID 키 상수 |
| `DFHBMSCA` | CICS 시스템 | BMS 화면 속성 상수 (DFH으로 시작하는 색상/보호 플래그) |

**호출 프로그램 (CALL/XCTL/LINK):**

| 동사 | 대상 프로그램 | 조건 |
|------|-------------|------|
| `EXEC CICS XCTL` | `COADM01C` | 인증 성공 + `CDEMO-USRTYP-ADMIN` (SEC-USR-TYPE = 'A') |
| `EXEC CICS XCTL` | `COMEN01C` | 인증 성공 + 일반 사용자 (SEC-USR-TYPE = 'U' 또는 비-A) |

**데이터셋/파일/DB 테이블:**

| 이름 | 유형 | 접근 방식 | 용도 |
|------|------|-----------|------|
| `USRSEC` | VSAM KSDS | `EXEC CICS READ ... RIDFLD(WS-USER-ID)` | 사용자 ID 키로 보안 레코드 랜덤 읽기 |

**트랜잭션 ID 또는 EXEC PGM:**

- 트랜잭션 ID: `CC00` (WS-TRANID, 라인 37)
- `EXEC CICS RETURN TRANSID('CC00')` 로 다음 입력 대기 → 키 입력 시 `CC00`으로 재기동

---

## 핵심 로직 흐름

```
[CC00 트랜잭션 기동]
        |
        v
MAIN-PARA
        |
        +-- EIBCALEN = 0? (최초 진입, COMMAREA 없음)
        |        |
        |       YES --> MOVE LOW-VALUES TO COSGN0AO    (화면 초기화)
        |               MOVE -1 TO USERIDL             (커서를 USERID 필드로)
        |               PERFORM SEND-SIGNON-SCREEN     (빈 로그인 화면 출력)
        |
        +-- EIBCALEN > 0? (재진입, 사용자가 키 입력)
                 |
                 +-- EIBAID = DFHENTER? --> PERFORM PROCESS-ENTER-KEY
                 |
                 +-- EIBAID = DFHPF3?   --> MOVE CCDA-MSG-THANK-YOU
                 |                          PERFORM SEND-PLAIN-TEXT
                 |                          (내부에서 EXEC CICS RETURN -- 트랜잭션 종료)
                 |
                 +-- OTHER             --> WS-ERR-FLG = 'Y'
                                          MOVE CCDA-MSG-INVALID-KEY
                                          PERFORM SEND-SIGNON-SCREEN

        (SEND-PLAIN-TEXT 경로 제외) 모든 경로 후:
        EXEC CICS RETURN TRANSID('CC00') COMMAREA(CARDDEMO-COMMAREA)
        --> 다음 키 입력 대기 (메모리에서 프로그램 제거)


PROCESS-ENTER-KEY (라인 108-140)
        |
        +-- EXEC CICS RECEIVE MAP('COSGN0A')  --> 화면 입력값을 COSGN0AI에 로드
        |
        +-- USERIDI = SPACES/LOW-VALUES?
        |        YES --> 오류 메시지 + 커서 USERID 필드 + SEND-SIGNON-SCREEN (즉시 RETURN)
        |
        +-- PASSWDI = SPACES/LOW-VALUES?
        |        YES --> 오류 메시지 + 커서 PASSWD 필드 + SEND-SIGNON-SCREEN (즉시 RETURN)
        |
        +-- FUNCTION UPPER-CASE(USERIDI) --> WS-USER-ID, CDEMO-USER-ID (대소문자 정규화)
        +-- FUNCTION UPPER-CASE(PASSWDI) --> WS-USER-PWD
        |
        +-- ERR-FLG-OFF? YES --> PERFORM READ-USER-SEC-FILE


READ-USER-SEC-FILE (라인 209-257)
        |
        +-- EXEC CICS READ DATASET('USRSEC')
        |       RIDFLD(WS-USER-ID)    -- User ID를 KSDS 키로 사용
        |       INTO(SEC-USER-DATA)
        |
        +-- WS-RESP-CD = 0 (레코드 발견)?
        |        |
        |        +-- SEC-USR-PWD = WS-USER-PWD? (평문 비밀번호 비교)
        |                 |
        |                YES --> 인증 성공 처리:
        |                        MOVE WS-TRANID    TO CDEMO-FROM-TRANID
        |                        MOVE WS-PGMNAME   TO CDEMO-FROM-PROGRAM
        |                        MOVE WS-USER-ID   TO CDEMO-USER-ID
        |                        MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
        |                        MOVE ZEROS        TO CDEMO-PGM-CONTEXT
        |                        |
        |                        +-- CDEMO-USRTYP-ADMIN? --> XCTL COADM01C
        |                        +-- else              --> XCTL COMEN01C
        |                        (XCTL 후 이 프로그램은 종료)
        |                 |
        |                NO  --> 'Wrong Password' + 커서 PASSWD + SEND-SIGNON-SCREEN
        |
        +-- WS-RESP-CD = 13 (NOTFND)?
        |        --> 'User not found' + 커서 USERID + SEND-SIGNON-SCREEN
        |
        +-- OTHER (시스템 오류)?
                 --> 'Unable to verify the User' + 커서 USERID + SEND-SIGNON-SCREEN


SEND-SIGNON-SCREEN (라인 145-157)
        |
        +-- PERFORM POPULATE-HEADER-INFO
        +-- MOVE WS-MESSAGE TO ERRMSGO
        +-- EXEC CICS SEND MAP('COSGN0A') FROM(COSGN0AO) ERASE CURSOR


POPULATE-HEADER-INFO (라인 177-204)
        |
        +-- FUNCTION CURRENT-DATE --> WS-CURDATE-DATA (연/월/일/시/분/초)
        +-- 날짜 MM/DD/YY 포맷 조립 --> CURDATEO
        +-- 시간 HH:MM:SS 포맷 조립 --> CURTIMEO
        +-- EXEC CICS ASSIGN APPLID --> APPLIDO  (CICS 애플리케이션 ID)
        +-- EXEC CICS ASSIGN SYSID  --> SYSIDO   (시스템 ID)
        +-- 제목/트랜잭션ID/프로그램명 화면 필드에 이동
```

**핵심 제어 흐름 주의사항:**

PROCESS-ENTER-KEY에서 `SEND-SIGNON-SCREEN`을 PERFORM한 후 단락이 끝나면 MAIN-PARA로 돌아와 마지막의 `EXEC CICS RETURN`이 실행된다. COBOL 단락은 PERFORM 호출 시 자동으로 복귀하므로 SEND 후 즉시 RETURN되는 효과가 발생한다. 이 "낙하(fall-through)" 방지를 위해 오류 경로는 SEND 후 자연스럽게 RETURN으로 흘러가도록 설계되어 있다.

EIBCALEN 커서 제어: `MOVE -1 TO USERIDL`은 커서를 해당 필드로 이동시키는 CICS BMS 메커니즘이다. `L` 서픽스 필드는 `PIC S9(4) COMP` 타입으로, -1을 MOVE하면 SEND MAP ... CURSOR 시 해당 필드에 커서가 위치한다.

---

## Java/현대화 노트

### 1. Pseudo-conversational 패턴 → Spring MVC Controller

```java
// COBOL: 매 CICS RETURN마다 프로그램 종료 후 재기동
// Java 등가: Stateless Controller + 세션

@Controller
@RequestMapping("/signon")
public class SignonController {

    // COBOL: EIBCALEN = 0 → 최초 진입
    @GetMapping
    public String showSignonForm(Model model) {
        model.addAttribute("signonForm", new SignonForm());
        return "signon"; // SEND-SIGNON-SCREEN에 해당
    }

    // COBOL: DFHENTER 처리 → PROCESS-ENTER-KEY
    @PostMapping
    public String processSignin(
            @Valid @ModelAttribute SignonForm form,
            BindingResult result,
            HttpSession session,
            Model model) {

        if (result.hasErrors()) {
            return "signon"; // SEND-SIGNON-SCREEN
        }

        UserSecRecord user = userSecRepository.findById(
                form.getUserId().toUpperCase());  // UPPER-CASE + READ-USER-SEC-FILE

        if (user == null) {
            model.addAttribute("errorMessage", "User not found. Try again ...");
            return "signon";
        }
        if (!user.getPassword().equals(form.getPassword().toUpperCase())) {
            model.addAttribute("errorMessage", "Wrong Password. Try again ...");
            return "signon";
        }

        // CDEMO-COMMAREA 세팅 → HttpSession에 저장
        session.setAttribute("userId", user.getId());
        session.setAttribute("userType", user.getType());

        // XCTL COADM01C / COMEN01C
        if ("A".equals(user.getType())) {
            return "redirect:/admin/menu";
        } else {
            return "redirect:/user/menu";
        }
    }
}
```

### 2. COMMAREA → HttpSession / JWT

`CARDDEMO-COMMAREA`는 고정 길이 바이트 구조체(`PIC X(01) OCCURS ... DEPENDING ON EIBCALEN`)로 직렬화된 세션 상태다. Java 현대화 시:
- **서블릿 기반**: `HttpSession`에 `UserSession` DTO 저장
- **토큰 기반(REST API)**: JWT Payload에 `userId`, `userType`, `fromProgram` 포함
- **중요**: `CDEMO-PGM-CONTEXT`(0=최초, 1=재진입)는 `EIBCALEN` 체크와 결합해 상태를 관리하므로, Java에서는 URL 구조 또는 세션 플래그로 대체한다.

### 3. USRSEC 파일 → UserRepository

```java
// COBOL: EXEC CICS READ DATASET('USRSEC') RIDFLD(WS-USER-ID)
// Java: JPA Repository (KSDS 키 = Primary Key)

@Entity
@Table(name = "USRSEC")
public class UserSecRecord {
    @Id
    @Column(name = "SEC_USR_ID", length = 8)
    private String id;            // PIC X(08)

    @Column(name = "SEC_USR_FNAME", length = 20)
    private String firstName;     // PIC X(20)

    @Column(name = "SEC_USR_LNAME", length = 20)
    private String lastName;      // PIC X(20)

    @Column(name = "SEC_USR_PWD", length = 8)
    private String password;      // PIC X(08) -- 평문 저장! 현대화 시 반드시 BCrypt 등으로 대체

    @Column(name = "SEC_USR_TYPE", length = 1)
    private String type;          // 'A'=admin, 'U'=user
}
```

### 4. 보안 경고: 평문 비밀번호

라인 223 `IF SEC-USR-PWD = WS-USER-PWD`는 USRSEC 파일에 저장된 비밀번호와 입력값을 **평문 그대로 비교**한다. 현대화 시 반드시 다음을 적용해야 한다:
- 비밀번호 해싱: `BCryptPasswordEncoder` 또는 `Argon2`
- 비밀번호 필드를 마이그레이션 시 자동으로 해싱된 값으로 변환
- 3270 화면에서는 비밀번호 필드가 `PASSWDF`(속성 바이트)로 화면에 표시되지 않으나, VSAM 파일에는 평문으로 저장됨

### 5. BMS Symbolic Map → HTML Form DTO

`COSGN00.CPY`의 `COSGN0AI`/`COSGN0AO` 구조는 BMS가 자동 생성한 3270 화면 I/O 구조체다. Java 현대화 대응표:

| COBOL BMS 필드 | Java DTO 필드 | 설명 |
|----------------|---------------|------|
| `USERIDI PIC X(8)` | `String userId` (max 8자) | 사용자 ID 입력 |
| `PASSWDI PIC X(8)` | `String password` (max 8자) | 비밀번호 입력 |
| `ERRMSGO PIC X(78)` | `String errorMessage` (max 78자) | 오류 메시지 출력 |
| `USERIDL PIC S9(4) COMP = -1` | `@ModelAttribute` cursor focus | 커서 위치 제어 (HTML에서는 `autofocus` 속성 또는 JS) |
| `CURDATEO PIC X(8)` | `LocalDate` 헤더 | MM/DD/YY 포맷 날짜 |
| `CURTIMEO PIC X(9)` | `LocalTime` 헤더 | HH:MM:SS 포맷 시각 |
| `APPLIDO PIC X(8)` | 서버 인스턴스 ID | `EXEC CICS ASSIGN APPLID` 대응 |
| `SYSIDO PIC X(8)` | 서버 시스템 ID | `EXEC CICS ASSIGN SYSID` 대응 |

### 6. CICS RESP 코드 → 예외 처리

```java
// COBOL: WS-RESP-CD = 0 → 정상, = 13 → NOTFND, OTHER → 시스템 오류
// Java: JPA/Repository 예외 매핑

try {
    UserSecRecord user = userSecRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found. Try again ..."));
    // RESP=0 정상 처리
} catch (UserNotFoundException e) {
    // RESP=13 (DFHRESP(NOTFND)) 대응
    model.addAttribute("errorMessage", e.getMessage());
    return "signon";
} catch (DataAccessException e) {
    // RESP=OTHER (시스템 오류) 대응
    model.addAttribute("errorMessage", "Unable to verify the User ...");
    return "signon";
}
```

### 7. XCTL → 내부 Redirect (돌아오지 않음)

`EXEC CICS XCTL`은 현재 프로그램을 종료하고 대상 프로그램으로 제어를 완전 이전한다. Java에서 `response.sendRedirect()`와 유사하지만, CICS XCTL은 같은 스레드(태스크) 내에서 COMMAREA를 그대로 전달하는 점이 다르다. Java 현대화 시 `return "redirect:..."` + 세션/토큰으로 상태를 전달하면 동등한 동작을 구현할 수 있다.

### 8. 마이그레이션 체크리스트

- [ ] USRSEC VSAM → 관계형 DB 테이블 `USER_SECURITY` 마이그레이션 (80바이트 고정 레코드 → 정규화된 행)
- [ ] 평문 비밀번호 → BCrypt 해싱으로 일괄 변환 후 컬럼 재저장
- [ ] CC00 트랜잭션 ID → Spring Security `UsernamePasswordAuthenticationFilter` 경로로 대체
- [ ] COMMAREA 세션 상태 → `HttpSession` 또는 JWT 클레임 매핑
- [ ] FUNCTION CURRENT-DATE → `LocalDateTime.now()` (타임존 명시 필요 — 메인프레임은 시스템 타임존, Java는 JVM 타임존)
- [ ] EIBCALEN = 0 분기 → GET 요청(최초 화면 표시)으로 자연스럽게 대체됨
- [ ] CDEMO-USRTYP-ADMIN 88-레벨 → Spring Security `Role.ADMIN` / `@PreAuthorize("hasRole('ADMIN')")`
