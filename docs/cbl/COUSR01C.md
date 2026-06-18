# COUSR01C — 사용자 신규 등록 화면 (User Add)

- **유형**: CICS 온라인 COBOL (Pseudo-conversational)
- **한 줄 요약**: 관리자가 신규 Regular/Admin 사용자를 입력 폼으로 등록하고 USRSEC VSAM 파일에 WRITE하는 CICS 온라인 프로그램.

---

## 기능 설명

COUSR01C는 CardDemo 관리자 메뉴(COADM01C)에서 호출되는 사용자 추가(User Add) 화면이다.
관리자가 성명, 사용자 ID, 비밀번호, 사용자 유형(A=관리자, U=일반)을 입력하면 5개 필드를 모두 검증한 뒤
USRSEC VSAM KSDS 파일에 새 레코드를 기록(EXEC CICS WRITE)한다.

- 등록 성공 시: 화면을 초기화하고 "User XXX has been added ..." 메시지를 녹색으로 표시한다.
- 중복 키(DUPKEY/DUPREC) 감지 시: "User ID already exist..." 오류 메시지를 표시하고 User ID 필드로 커서를 이동한다.
- 기타 CICS 오류 시: "Unable to Add User..." 메시지를 표시한다.
- PF3: 관리자 메뉴(COADM01C)로 복귀(XCTL).
- PF4: 현재 화면 필드 초기화(지우기).

---

## 입력 / 출력

**입력:**
- `DFHCOMMAREA` (LINKAGE SECTION): 이전 프로그램에서 전달된 `CARDDEMO-COMMAREA` (최대 32,767 바이트, EIBCALEN에 따른 가변). 세션 상태(CDEMO-PGM-REENTER 플래그, 이전/다음 프로그램 이름 등) 포함.
- BMS 입력 맵 `COUSR1AI` (맵셋 COUSR01): 사용자가 터미널에서 입력한 5개 필드:
  - `FNAMEI` (PIC X(20)): 이름
  - `LNAMEI` (PIC X(20)): 성
  - `USERIDI` (PIC X(8)): 사용자 ID (KSDS 키)
  - `PASSWDI` (PIC X(8)): 비밀번호
  - `USRTYPEI` (PIC X(1)): 사용자 유형 ('A' 또는 'U')
- `EIBAID`: CICS가 제공하는 "마지막으로 누른 키" 코드 (ENTER/PF3/PF4 판별에 사용).
- `EIBCALEN`: commarea 길이 (0이면 최초 진입).

**출력:**
- BMS 출력 맵 `COUSR1AO` (COUSR1AI와 동일 메모리를 REDEFINES): 화면 전송용. 헤더(제목·날짜·시간·트랜잭션 ID·프로그램명)와 오류 메시지 필드(`ERRMSGO`, 색상 속성 `ERRMSGC`) 포함.
- `USRSEC` VSAM KSDS 파일: 신규 사용자 레코드 1건 기록. 레이아웃은 `SEC-USER-DATA` (80바이트) 참조.
- 화면으로 전송되는 상태 메시지 (`WS-MESSAGE`, PIC X(80)).

---

## 의존성

**COPY (카피북):**

| Copybook | 경로 | 역할 |
|---|---|---|
| `COCOM01Y` | `app/cpy/COCOM01Y.cpy` | `CARDDEMO-COMMAREA` 정의 — 세션 상태 객체 (프로그램명, 사용자 유형, REENTER 플래그 등) |
| `COUSR01` | `app/cpy-bms/COUSR01.CPY` | BMS symbolic map — `COUSR1AI`(입력) / `COUSR1AO`(출력, REDEFINES) |
| `COTTL01Y` | `app/cpy/COTTL01Y.cpy` | 화면 타이틀 상수 (`CCDA-TITLE01`, `CCDA-TITLE02`) |
| `CSDAT01Y` | `app/cpy/CSDAT01Y.cpy` | 현재 날짜·시간 작업 영역 (`WS-DATE-TIME` 구조체 및 포맷 필드) |
| `CSMSG01Y` | `app/cpy/CSMSG01Y.cpy` | 공통 메시지 상수 (`CCDA-MSG-INVALID-KEY` 등) |
| `CSUSR01Y` | `app/cpy/CSUSR01Y.cpy` | `SEC-USER-DATA` 레코드 레이아웃 (80바이트, USRSEC 파일 I/O 버퍼) |
| `DFHAID` | CICS 시스템 제공 | `DFHENTER`, `DFHPF3`, `DFHPF4` 등 EIBAID 키 상수 |
| `DFHBMSCA` | CICS 시스템 제공 | `DFHGREEN`, `DFHRED` 등 화면 속성(색상) 상수 |

**호출 프로그램 (CALL/XCTL/LINK):**

| 호출 방식 | 대상 프로그램 | 조건 |
|---|---|---|
| `EXEC CICS XCTL` | `COADM01C` | PF3 키 — 관리자 메뉴로 복귀 |
| `EXEC CICS XCTL` | `COSGN00C` | EIBCALEN=0(최초 진입) 또는 CDEMO-TO-PROGRAM이 공백일 때 기본 복귀 대상 |

CALL(정적 서브루틴 호출)은 없다. 프로그램 간 이동은 전부 XCTL(단방향 제어 이전)이다.

**데이터셋/파일/DB 테이블:**

| 이름 | 유형 | 접근 | 비고 |
|---|---|---|---|
| `USRSEC` | VSAM KSDS | WRITE (신규 레코드 추가) | 키: `SEC-USR-ID` (PIC X(8)), 레코드 80바이트. 중복 키 → `DFHRESP(DUPKEY)` 또는 `DFHRESP(DUPREC)` |

DB2 SQL 접근 없음.

**트랜잭션 ID 또는 EXEC PGM:**

- CICS 트랜잭션 ID: **`CU01`** (`WS-TRANID`, 38라인)
- `EXEC CICS RETURN TRANSID('CU01') COMMAREA(...)` 로 매 턴 종료 — 다음 키 입력 시 CU01 트랜잭션이 COUSR01C를 재기동한다.

---

## 핵심 로직 흐름

### 전체 흐름 개요

```
최초 요청 (EIBCALEN=0)
    → COSGN00C로 XCTL (비정상 경로; 정상은 COADM01C에서 XCTL로 진입)

정상 진입 (EIBCALEN > 0)
    → COMMAREA 복원
    ┌─ CDEMO-PGM-REENTER = FALSE (첫 번째 도착)
    │       CDEMO-PGM-REENTER = TRUE 설정
    │       COUSR1AO 초기화 (LOW-VALUES)
    │       커서 위치 → FNAMEL = -1
    │       SEND-USRADD-SCREEN → 빈 입력 폼 표시
    │       EXEC CICS RETURN (TRANSID=CU01) → 대기
    │
    └─ CDEMO-PGM-REENTER = TRUE (사용자 응답 수신)
            RECEIVE-USRADD-SCREEN (COUSR1AI 수신)
            EVALUATE EIBAID
                DFHENTER → PROCESS-ENTER-KEY
                DFHPF3   → RETURN-TO-PREV-SCREEN (COADM01C)
                DFHPF4   → CLEAR-CURRENT-SCREEN
                OTHER    → 오류 메시지 + SEND-USRADD-SCREEN
            EXEC CICS RETURN (TRANSID=CU01) → 대기
```

### PROCESS-ENTER-KEY 상세

```
EVALUATE TRUE (순서 보장 — 첫 번째 빈 필드에서 즉시 분기)
    FNAMEI = 공백/LOW-VALUES  → WS-ERR-FLG='Y', 오류 메시지, SEND-USRADD-SCREEN
    LNAMEI = 공백/LOW-VALUES  → WS-ERR-FLG='Y', 오류 메시지, SEND-USRADD-SCREEN
    USERIDI = 공백/LOW-VALUES → WS-ERR-FLG='Y', 오류 메시지, SEND-USRADD-SCREEN
    PASSWDI = 공백/LOW-VALUES → WS-ERR-FLG='Y', 오류 메시지, SEND-USRADD-SCREEN
    USRTYPEI = 공백/LOW-VALUES→ WS-ERR-FLG='Y', 오류 메시지, SEND-USRADD-SCREEN
    OTHER → CONTINUE (모두 통과)

IF NOT ERR-FLG-ON (오류 없음)
    COUSR1AI 5개 필드 → SEC-USER-DATA로 MOVE
    PERFORM WRITE-USER-SEC-FILE
```

주의: EVALUATE WHEN OTHER의 `CONTINUE` 후 프로그램은 `IF NOT ERR-FLG-ON` 블록으로 자연스럽게 진행(fall-through)한다. 각 오류 분기에서 `PERFORM SEND-USRADD-SCREEN`을 호출하지만 PERFORM은 CICS RETURN을 수행하지 않으므로, SEND 후 EVALUATE를 탈출해 `IF NOT ERR-FLG-ON`에 다시 도달한다. `WS-ERR-FLG='Y'` 설정이 이 중복 진입을 막는 가드이다.

### WRITE-USER-SEC-FILE 상세

```
EXEC CICS WRITE
    DATASET('USRSEC')
    FROM(SEC-USER-DATA)          ← 80바이트 레코드
    LENGTH(LENGTH OF SEC-USER-DATA)
    RIDFLD(SEC-USR-ID)           ← PIC X(8) 키
    KEYLENGTH(LENGTH OF SEC-USR-ID)
    RESP(WS-RESP-CD) RESP2(WS-REAS-CD)

EVALUATE WS-RESP-CD
    DFHRESP(NORMAL)  → INITIALIZE-ALL-FIELDS + 성공 메시지(녹색) + SEND-USRADD-SCREEN
    DFHRESP(DUPKEY)
    DFHRESP(DUPREC)  → WS-ERR-FLG='Y' + "User ID already exist..." + SEND-USRADD-SCREEN
    OTHER            → WS-ERR-FLG='Y' + "Unable to Add User..." + SEND-USRADD-SCREEN
```

### POPULATE-HEADER-INFO

`FUNCTION CURRENT-DATE`를 호출해 8자리 날짜(YYYYMMDD)와 시분초를 얻고, MM/DD/YY 및 HH:MM:SS 포맷으로 변환해 COUSR1AO 헤더 필드에 채운다. 모든 화면 SEND 전에 호출된다.

### INITIALIZE-ALL-FIELDS

등록 성공 후 또는 PF4(지우기) 시 호출. 5개 입력 필드를 공백으로 초기화하고 커서를 FNAME으로 돌려보낸다.

---

## Java/현대화 노트

### 1. Pseudo-conversational 패턴 → HTTP 세션 + Controller

CICS pseudo-conversational 방식은 HTTP 무상태 요청과 구조가 동일하다. EIBCALEN, CDEMO-PGM-REENTER, `EXEC CICS RETURN TRANSID COMMAREA`의 Java 대응:

```java
// COBOL: EXEC CICS RETURN TRANSID('CU01') COMMAREA(CARDDEMO-COMMAREA)
// Java: HttpSession에 commarea 역할의 세션 객체 저장 후 응답 반환
@PostMapping("/users/add")
public String addUser(@SessionAttribute CardDemoSession session,
                      @ModelAttribute UserAddForm form,
                      Model model) {
    // CDEMO-PGM-REENTER 역할: 세션에 "이미 화면 표시됨" 상태 없음
    // 항상 POST = RECEIVE-USRADD-SCREEN 경로
}
```

### 2. SEC-USER-DATA 레코드 레이아웃 → Java DTO

```java
// CSUSR01Y.cpy의 SEC-USER-DATA (80바이트 고정 길이)
public class SecUserData {
    private String secUsrId;    // PIC X(8)  — 최대 8자 영숫자, KSDS 키
    private String secUsrFname; // PIC X(20) — 이름
    private String secUsrLname; // PIC X(20) — 성
    private String secUsrPwd;   // PIC X(8)  — 평문 비밀번호 (주의: 암호화 없음!)
    private String secUsrType;  // PIC X(1)  — 'A'(관리자) / 'U'(일반)
    // SEC-USR-FILLER PIC X(23) — 패딩, Java에서는 불필요
}
```

**보안 경고**: COBOL 원본은 비밀번호를 `PIC X(8)` 평문으로 저장한다 (COUSR01C.cbl 156-157라인). 현대화 시 반드시 BCrypt 등 단방향 해시로 교체해야 한다.

### 3. EVALUATE TRUE (순서 보장 검증) → Bean Validation / if-else chain

```java
// COBOL EVALUATE TRUE의 "첫 번째 매칭에서 즉시 종료" 패턴 → 우선순위 if-else
public String validateAndSave(UserAddForm form, RedirectAttributes ra) {
    if (form.getFname() == null || form.getFname().isBlank()) {
        ra.addFlashAttribute("errorMsg", "First Name can NOT be empty...");
        return "redirect:/users/add";
    }
    if (form.getLname() == null || form.getLname().isBlank()) { ... }
    if (form.getUserId() == null || form.getUserId().isBlank()) { ... }
    if (form.getPassword() == null || form.getPassword().isBlank()) { ... }
    if (form.getUserType() == null || form.getUserType().isBlank()) { ... }
    // 모두 통과 → 저장
    userRepository.save(toEntity(form));
    return "redirect:/users/add?success";
}
// 또는 Spring @Valid + @NotBlank 어노테이션으로 일괄 처리
```

### 4. EXEC CICS WRITE → Repository save / 중복 키 처리

```java
// COBOL DFHRESP(DUPKEY) → JPA DataIntegrityViolationException
try {
    userRepository.save(user); // PK = userId
} catch (DataIntegrityViolationException e) {
    model.addAttribute("errorMsg", "User ID already exist...");
    return "users/add";
}
```

### 5. XCTL vs RETURN → redirect vs forward

```
EXEC CICS XCTL PROGRAM('COADM01C') COMMAREA(...)
```
→ XCTL은 제어를 완전히 넘기고 돌아오지 않는다. Java에서는 `return "redirect:/admin/menu";`(PF3), 또는 메서드 체이닝/서비스 위임 패턴.

### 6. BMS symbolic map (COUSR1AI/COUSR1AO REDEFINES) → 단일 DTO

COUSR1AO는 COUSR1AI와 동일한 바이트 배열을 REDEFINES로 공유한다 (`app/cpy-bms/COUSR01.CPY` 91라인). 입력은 ...I 뷰, 출력은 ...O 뷰를 사용하며 Java에는 직접 대응이 없다. 현대화 시 하나의 `UserAddForm` DTO로 통합한다:

```java
public class UserAddForm {
    @NotBlank String fname;     // FNAMEI  PIC X(20)
    @NotBlank String lname;     // LNAMEI  PIC X(20)
    @NotBlank @Size(max=8) String userId;   // USERIDI PIC X(8)
    @NotBlank @Size(max=8) String password; // PASSWDI PIC X(8)
    @Pattern(regexp="[AU]") String userType; // USRTYPEI PIC X(1)
    String errorMsg;            // ERRMSGO PIC X(78) — 화면 오류 메시지
}
```

### 7. 커서 위치 제어 (FNAMEL = -1) → autofocus / focus()

COBOL의 `MOVE -1 TO FNAMEL OF COUSR1AI`는 BMS에게 "이 필드에 커서를 놓아라"는 신호다. Java/HTML에서는 `<input autofocus>` 또는 Thymeleaf `th:autofocus`로 대응한다.

### 8. 날짜/시간 처리 → LocalDateTime

`FUNCTION CURRENT-DATE`와 CSDAT01Y의 수동 포맷 조합은 Java의 `LocalDateTime.now()`와 `DateTimeFormatter`로 대체한다.

### 9. 사용자 유형 검증 부재

현재 코드는 `USRTYPEI`가 공백/LOW-VALUES가 아니면 곧바로 WRITE한다. 'A'/'U' 이외의 값에 대한 검증이 없다 (153-160라인). 현대화 시 enum 또는 `@Pattern(regexp="[AU]")` 검증을 추가해야 한다.

---

### 참고: USRSEC 파일 레코드 구조

| 필드 | PIC | 바이트 | Java 타입 | 비고 |
|---|---|---|---|---|
| `SEC-USR-ID` | X(8) | 8 | `String` (max 8) | KSDS 키, 공백 불가 |
| `SEC-USR-FNAME` | X(20) | 20 | `String` (max 20) | 이름 |
| `SEC-USR-LNAME` | X(20) | 20 | `String` (max 20) | 성 |
| `SEC-USR-PWD` | X(8) | 8 | `String` → 현대화 시 해시 | 평문 저장 주의 |
| `SEC-USR-TYPE` | X(1) | 1 | `enum UserType {ADMIN,USER}` | 'A'=관리자, 'U'=일반 |
| `SEC-USR-FILLER` | X(23) | 23 | (없음) | 패딩 |
| **합계** | | **80** | | |
