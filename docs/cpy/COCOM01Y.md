# COCOM01Y — CardDemo 전역 세션 COMMAREA

- **유형**: Copybook
- **한 줄 요약**: 모든 CICS 온라인 프로그램이 공유하는 의사대화형(pseudo-conversational) 세션 컨텍스트 — 화면 전환(XCTL/RETURN)마다 전달되는 상태 객체이며, HTTP 세션(HttpSession)이나 Spring Security의 SecurityContext에 해당한다.

---

## 기능 설명

COBOL CICS 환경에서는 트랜잭션이 응답을 보낸 뒤 즉시 종료되고, 다음 사용자 입력이 도착했을 때 프로그램이 다시 시작된다. 이를 **의사대화형(pseudo-conversational) 패턴**이라고 부른다. 프로그램 인스턴스 사이에 메모리가 유지되지 않기 때문에, 매 재진입(REENTER) 때 필요한 상태를 별도 영역에 직렬화해 전달해야 한다. 그 역할을 하는 것이 **COMMAREA**이다.

`COCOM01Y`는 CardDemo 애플리케이션 전체에서 단일 COMMAREA 레이아웃을 정의한다. CO로 시작하는 모든 온라인 프로그램(COADM01C, COCRDLI C, COCRDUP C 등)이 이 copybook을 `COPY`하여 동일한 오프셋으로 필드를 읽고 쓴다.

- **01 레벨명**: `CARDDEMO-COMMAREA`
- **총 크기**: 4+8+4+8+8+1+1+9+25+25+25+11+1+16+7+7 = **160바이트** (고정 길이, DISPLAY 전용)

> **CICS 동작 원리**: `EXEC CICS RETURN TRANSID(…) COMMAREA(CARDDEMO-COMMAREA)` 명령이 이 160바이트를 CICS가 관리하는 임시 저장소에 저장한다. 다음 트랜잭션이 시작되면 `EXEC CICS RECEIVE MAP` 이전에 `DFHCOMMAREA`(LINKAGE SECTION)를 통해 이 영역이 프로그램에 주입된다.

---

## 필드 레이아웃

### 05 CDEMO-GENERAL-INFO — 내비게이션·사용자·상태

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `CDEMO-FROM-TRANID` | `PIC X(04)` | 4 | 이전 화면의 CICS 트랜잭션 ID (예: `CC00`). 뒤로 가기(Back) 구현에 사용. |
| `CDEMO-FROM-PROGRAM` | `PIC X(08)` | 8 | 이전 화면 프로그램명 (예: `COMEN01C`). |
| `CDEMO-TO-TRANID` | `PIC X(04)` | 4 | 다음으로 이동할 트랜잭션 ID. `XCTL`/`RETURN` 전에 세팅. |
| `CDEMO-TO-PROGRAM` | `PIC X(08)` | 8 | 다음으로 이동할 프로그램명. |
| `CDEMO-USER-ID` | `PIC X(08)` | 8 | 로그인한 사용자 ID (공백 패딩). |
| `CDEMO-USER-TYPE` | `PIC X(01)` | 1 | 사용자 권한 타입. 아래 88-level 참조. |
| &nbsp;&nbsp;`88 CDEMO-USRTYP-ADMIN` | `VALUE 'A'` | — | `CDEMO-USER-TYPE = 'A'`일 때 참(true). 관리자 메뉴 분기 조건. |
| &nbsp;&nbsp;`88 CDEMO-USRTYP-USER` | `VALUE 'U'` | — | `CDEMO-USER-TYPE = 'U'`일 때 참(true). 일반 사용자 메뉴 분기 조건. |
| `CDEMO-PGM-CONTEXT` | `PIC 9(01)` | 1 | 프로그램 진입 상태(0=최초, 1=재진입). 아래 88-level 참조. |
| &nbsp;&nbsp;`88 CDEMO-PGM-ENTER` | `VALUE 0` | — | 처음 화면 진입(초기화 필요). |
| &nbsp;&nbsp;`88 CDEMO-PGM-REENTER` | `VALUE 1` | — | 사용자 입력 후 재진입(입력 처리 필요). |

### 05 CDEMO-CUSTOMER-INFO — 현재 작업 대상 고객

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `CDEMO-CUST-ID` | `PIC 9(09)` | 9 | 고객 번호(숫자 9자리, DISPLAY). |
| `CDEMO-CUST-FNAME` | `PIC X(25)` | 25 | 고객 이름(First Name), 공백 우측 패딩. |
| `CDEMO-CUST-MNAME` | `PIC X(25)` | 25 | 고객 중간 이름(Middle Name). |
| `CDEMO-CUST-LNAME` | `PIC X(25)` | 25 | 고객 성(Last Name). |

### 05 CDEMO-ACCOUNT-INFO — 현재 작업 대상 계좌

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `CDEMO-ACCT-ID` | `PIC 9(11)` | 11 | 계좌 번호(숫자 11자리, DISPLAY). |
| `CDEMO-ACCT-STATUS` | `PIC X(01)` | 1 | 계좌 상태 코드(예: `A`=활성, `C`=해지 등, 값 정의는 별도 copybook). |

### 05 CDEMO-CARD-INFO — 현재 작업 대상 카드

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `CDEMO-CARD-NUM` | `PIC 9(16)` | 16 | 카드 번호(숫자 16자리, DISPLAY). |

### 05 CDEMO-MORE-INFO — 이전 화면 정보(Back Navigation 보조)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `CDEMO-LAST-MAP` | `PIC X(7)` | 7 | 직전에 표시했던 BMS 맵 이름. |
| `CDEMO-LAST-MAPSET` | `PIC X(7)` | 7 | 직전 맵이 속한 BMS 맵셋 이름. |

---

## 의존성

- **COPY (중첩 카피북)**: 없음. 이 copybook 자체가 다른 copybook을 `COPY`하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음. 데이터 레이아웃 정의 전용이며, 실행 로직을 포함하지 않는다.
- **데이터셋/파일/DB 테이블**: 없음. COMMAREA는 CICS 내부 임시 저장소에 의해 전달될 뿐, 파일이나 DB에 직접 저장되지 않는다.
- **트랜잭션 ID 또는 EXEC PGM**: 없음.

이 copybook을 `COPY`하는 주요 프로그램 (소스 탐색 근거):

| 프로그램 | 역할 |
|---|---|
| `COADM01C` | 관리자 메인 메뉴 (`CDEMO-USRTYP-ADMIN` 분기) |
| `COMEN01C` | 일반 사용자 메인 메뉴 (`CDEMO-USRTYP-USER` 분기) |
| `COSGN00C` | 로그인 처리 (`CDEMO-USER-ID`, `CDEMO-USER-TYPE` 세팅) |
| `COCRDLIC`, `COCRDUPC` 등 카드 조회/수정 프로그램 | `CDEMO-CARD-NUM`, `CDEMO-ACCT-ID`, `CDEMO-CUST-ID` 참조 |

---

## Java/현대화 노트

### 1. COMMAREA = 직렬화된 세션 객체

CICS COMMAREA는 Java 웹 애플리케이션의 `HttpSession` 또는 Spring Security `SecurityContext`와 기능적으로 동일하다. 단, HTTP 세션은 서버 힙에 남아 있지만 COMMAREA는 매 트랜잭션 종료 시 **직렬화되어 전달**되고 재진입 시 **역직렬화**된다는 점이 다르다.

```java
// Java 현대화 시 권장 매핑
public class CardDemoSession implements Serializable {
    // CDEMO-GENERAL-INFO
    private String fromTransId;      // PIC X(04) → String (max 4)
    private String fromProgram;      // PIC X(08) → String (max 8)
    private String toTransId;        // PIC X(04) → String (max 4)
    private String toProgram;        // PIC X(08) → String (max 8)
    private String userId;           // PIC X(08) → String (max 8)
    private UserType userType;       // PIC X(01) + 88-level → enum
    private ProgramContext pgmContext; // PIC 9(01) + 88-level → enum

    // CDEMO-CUSTOMER-INFO
    private long custId;             // PIC 9(09) → long (9자리 최대 10억)
    private String custFirstName;    // PIC X(25) → String
    private String custMiddleName;   // PIC X(25) → String
    private String custLastName;     // PIC X(25) → String

    // CDEMO-ACCOUNT-INFO
    private long acctId;             // PIC 9(11) → long (11자리)
    private String acctStatus;       // PIC X(01) → String 또는 enum

    // CDEMO-CARD-INFO
    private String cardNum;          // PIC 9(16) → String (카드번호는 수치 연산 없음)

    // CDEMO-MORE-INFO
    private String lastMap;          // PIC X(7) → String
    private String lastMapset;       // PIC X(7) → String
}

public enum UserType {
    ADMIN('A'), USER('U');           // 88 CDEMO-USRTYP-ADMIN VALUE 'A'
    private final char code;
}

public enum ProgramContext {
    ENTER(0), REENTER(1);            // 88 CDEMO-PGM-ENTER VALUE 0
    private final int code;
}
```

### 2. 88-level condition name = boolean 술어 / enum

COBOL의 `88 CDEMO-PGM-REENTER VALUE 1`은 Java의 `pgmContext == ProgramContext.REENTER`와 동일하다. 프로그램에서 다음처럼 분기 조건으로 사용된다.

```cobol
* COBOL: 재진입 여부 확인
IF CDEMO-PGM-REENTER
    PERFORM PROCESS-USER-INPUT
ELSE
    PERFORM INITIALIZE-SCREEN
END-IF
```

```java
// Java 동치 코드
if (session.getPgmContext() == ProgramContext.REENTER) {
    processUserInput(session);
} else {
    initializeScreen(session);
}
```

### 3. PIC 9(09) / PIC 9(11) — DISPLAY 숫자, 수치 연산 주의

`CDEMO-CUST-ID`(9자리)와 `CDEMO-ACCT-ID`(11자리)는 `COMP`/`COMP-3` 없이 `PIC 9(n)`만 선언되어 있으므로 **DISPLAY 형식**(각 바이트가 EBCDIC 숫자 한 자리)이다. Java 매핑 시:
- `int` 범위를 초과하지 않으면 `int` 사용 가능하나, 11자리(최대 99,999,999,999)는 `long` 필수.
- 카드 번호 `PIC 9(16)`은 수치 연산 없이 식별자로만 사용되므로 `String` 권장 (Long overflow 위험 및 가독성).

### 4. 고정 길이 문자열 — 공백 패딩 처리

`PIC X(25)` 필드(`CDEMO-CUST-FNAME` 등)는 COBOL에서 항상 25바이트이며 우측이 공백으로 패딩된다. Java 변환 시 반드시 `trim()` 처리해야 한다.

```java
String firstName = rawCommarea.getCustFirstName().stripTrailing();
```

### 5. EBCDIC vs ASCII

CICS 환경에서 COMMAREA는 **EBCDIC** 인코딩으로 전달된다. 메인프레임과 Java 애플리케이션이 직접 통신하는 경우(예: IBM MQ, CICS Web Services) 반드시 인코딩 변환(`Cp037` 또는 `IBM-037` 코드페이지)을 적용해야 한다.

### 6. 내비게이션 흐름 — FROM/TO 필드의 역할

`CDEMO-FROM-TRANID` + `CDEMO-FROM-PROGRAM`은 **뒤로 가기(Back)** 기능의 핵심이다. 화면 전환 전에 현재 트랜잭션 ID와 프로그램명을 FROM 필드에 저장하고, TO 필드에 목적지를 세팅한 후 `XCTL`을 호출한다. Java Spring MVC로 치면 `Referer` 헤더 + `RedirectAttributes`에 해당하나, COBOL에서는 이를 명시적으로 COMMAREA에 기록한다.

```
화면 A (COADM01C) → 화면 B (COCRDLIC) 전환 시:
  CDEMO-FROM-TRANID  = 'CC00'         (A의 트랜잭션 ID)
  CDEMO-FROM-PROGRAM = 'COADM01C'     (A의 프로그램명)
  CDEMO-TO-TRANID    = 'CC02'         (B의 트랜잭션 ID)
  CDEMO-TO-PROGRAM   = 'COCRDLIC'     (B의 프로그램명)
  EXEC CICS XCTL PROGRAM('COCRDLIC') COMMAREA(CARDDEMO-COMMAREA)
```

---

*소스 참조: `/app/cpy/COCOM01Y.cpy` line 19–45. CardDemo v1.0-15-g27d6c6f-68 (2022-07-19).*
