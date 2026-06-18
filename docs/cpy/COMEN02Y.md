# COMEN02Y — 일반 사용자 메인 메뉴 옵션 테이블

- **유형**: Copybook
- **한 줄 요약**: 일반 사용자(`USRTYPE='U'`) 화면에 표시되는 메뉴 항목 11개(번호·표시명·이동 대상 프로그램명·사용자 유형)를 고정 배열로 정의하는 데이터 전용 copybook으로, COMEN01C 메뉴 프로그램이 COPY하여 데이터 주도 XCTL 라우팅에 사용한다.

---

## 기능 설명

`COMEN02Y`는 PROCEDURE DIVISION 코드가 전혀 없는 순수 데이터 정의 copybook이다.
`CARDDEMO-MAIN-MENU-OPTIONS`(01레벨) 아래에 두 개의 05레벨 구조를 선언한다.

1. **`CDEMO-MENU-OPT-COUNT`** (라인 21): 유효 옵션 수 = `11`. 메뉴 프로그램이 이 값을 상한으로 삼아 반복문을 제어한다.

2. **`CDEMO-MENU-OPTIONS-DATA`** (라인 23): 옵션 11개분의 raw 바이트를 FILLER 시퀀스로 기술한 비구조적 블록. 각 항목은 4개의 FILLER 필드(`9(02)` 번호 + `X(35)` 이름 + `X(08)` 프로그램명 + `X(01)` 사용자유형)로 구성되며 합계 **46바이트**가 연속 배치된다.

3. **`CDEMO-MENU-OPTIONS REDEFINES CDEMO-MENU-OPTIONS-DATA`** (라인 93): 위의 raw 바이트를 구조화된 배열 뷰로 겹쳐 보는 선언. `OCCURS 12 TIMES`로 슬롯을 12개 예약하지만 실제 데이터는 11개만 초기화되어 있다(슬롯 12는 미정의, 0/스페이스). 프로그램은 `CDEMO-MENU-OPT-COUNT`(=11)까지만 순회하므로 슬롯 12는 접근되지 않는다.

메뉴 프로그램 COMEN01C는 이 배열을 순회하며 각 항목의 `CDEMO-MENU-OPT-PGMNAME`을 `EXEC CICS XCTL PROGRAM(...)` 대상으로 직접 전달한다 — **데이터 주도(data-driven) 라우팅** 패턴. 선택적 모듈(옵션 11, `COPAUS0C`)은 `EXEC CICS INQUIRE PROGRAM ... NOHANDLE`로 설치 여부를 탐지한 뒤, 미설치 시 "not installed" 메시지를 표시한다.

---

## 필드 레이아웃

### 01 CARDDEMO-MAIN-MENU-OPTIONS

| 레벨 | 필드명 | PIC / USAGE | 바이트 | 의미 |
|------|--------|-------------|--------|------|
| 05 | `CDEMO-MENU-OPT-COUNT` | `PIC 9(02)` VALUE 11 | 2 | 활성 메뉴 항목 수 (루프 상한) |
| 05 | `CDEMO-MENU-OPTIONS-DATA` | (group) | 506 | 11개 항목 × 46바이트 raw FILLER 블록 |
| 05 | `CDEMO-MENU-OPTIONS` | **REDEFINES** `CDEMO-MENU-OPTIONS-DATA` | 552 | 배열 뷰 (OCCURS 12 TIMES × 46바이트) |
| &nbsp;&nbsp;10 | `CDEMO-MENU-OPT` | **OCCURS 12 TIMES** | 46 | 개별 메뉴 항목 (인덱스 1~12) |
| &nbsp;&nbsp;&nbsp;&nbsp;15 | `CDEMO-MENU-OPT-NUM` | `PIC 9(02)` | 2 | 항목 일련번호 (표시용, 1~11) |
| &nbsp;&nbsp;&nbsp;&nbsp;15 | `CDEMO-MENU-OPT-NAME` | `PIC X(35)` | 35 | 화면에 표시되는 메뉴 항목 이름 (우측 공백 패딩) |
| &nbsp;&nbsp;&nbsp;&nbsp;15 | `CDEMO-MENU-OPT-PGMNAME` | `PIC X(08)` | 8 | CICS XCTL 대상 프로그램명 (공백 없이 8자 고정) |
| &nbsp;&nbsp;&nbsp;&nbsp;15 | `CDEMO-MENU-OPT-USRTYPE` | `PIC X(01)` | 1 | 허용 사용자 유형 (`'U'` = 일반 사용자) |

> **REDEFINES 주의**: `CDEMO-MENU-OPTIONS`와 `CDEMO-MENU-OPTIONS-DATA`는 동일한 메모리 주소를 공유한다. FILLER 블록(11×46 = 506바이트)과 OCCURS 배열(12×46 = 552바이트) 사이에 **46바이트(1슬롯) 크기 불일치**가 있다. COBOL은 REDEFINES 시 크기 검사를 강제하지 않으며, 슬롯 12는 `CDEMO-MENU-OPTIONS-DATA` 경계 밖 메모리를 참조하게 된다. 실제로는 `CDEMO-MENU-OPT-COUNT`(=11) 상한 덕분에 슬롯 12에는 접근하지 않으나, 마이그레이션 시 명시적 경계 처리가 필요하다.

### 메뉴 옵션 목록 (초기화 값 기준)

| 인덱스 | `OPT-NUM` | `OPT-NAME` | `OPT-PGMNAME` | `OPT-USRTYPE` | 비고 |
|--------|-----------|------------|---------------|---------------|------|
| 1 | 1 | `Account View` | `COACTVWC` | U | 계정 조회 |
| 2 | 2 | `Account Update` | `COACTUPC` | U | 계정 수정 |
| 3 | 3 | `Credit Card List` | `COCRDLIC` | U | 카드 목록 |
| 4 | 4 | `Credit Card View` | `COCRDSLC` | U | 카드 상세 조회 |
| 5 | 5 | `Credit Card Update` | `COCRDUPC` | U | 카드 수정 |
| 6 | 6 | `Transaction List` | `COTRN00C` | U | 거래 목록 |
| 7 | 7 | `Transaction View` | `COTRN01C` | U | 거래 상세 조회 |
| 8 | 8 | `Transaction Add` | `COTRN02C` | U | 거래 입력 (라인 69 주석에 "Admin Only" 흔적 있음) |
| 9 | 9 | `Transaction Reports` | `CORPT00C` | U | 거래 보고서 |
| 10 | 10 | `Bill Payment` | `COBIL00C` | U | 청구 결제 |
| 11 | 11 | `Pending Authorization View` | `COPAUS0C` | U | **선택 모듈** — IMS/MQ 승인 모듈, 미설치 시 "not installed" |
| 12 | (미초기화) | — | — | — | OCCURS 예약 슬롯, 접근 안 함 |

> **라인 69 주석**: 옵션 8의 이름이 원래 `'Transaction Add (Admin Only)'`였다가 `'Transaction Add'`로 변경된 흔적이 주석으로 남아 있다. 현재 메뉴는 사용자 유형 구분 없이 `'U'`로 통일되어 있으므로, 권한 제어가 필요하다면 별도 로직이 필요하다.

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — 이 copybook 자체가 다른 copybook을 COPY하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음 — 데이터 전용 copybook이므로 직접적인 프로그램 호출이 없다.
- **데이터셋/파일/DB 테이블**: 없음 — 컴파일 시 정적으로 초기화되는 상수 테이블이므로 런타임 파일/DB 접근이 없다.
- **트랜잭션 ID 또는 EXEC PGM**: 없음.

이 copybook을 COPY하는 주요 프로그램:

| 프로그램 | 역할 |
|----------|------|
| `COMEN01C` | 일반 사용자 메뉴 화면. `CDEMO-MENU-OPT-COUNT`와 `CDEMO-MENU-OPT-PGMNAME`을 직접 사용해 XCTL 라우팅 수행. |

---

## Java/현대화 노트

### 1. REDEFINES + OCCURS 패턴 → Java `List<MenuOption>`

COBOL의 핵심 기법은 동일 메모리를 두 가지 뷰로 보는 것이다. Java에는 직접 대응물이 없으며, 정적 초기화 리스트로 대체한다.

```java
// COBOL: CDEMO-MENU-OPT-NAME PIC X(35) — 우측 공백 패딩 고정길이
// Java: trim() 후 String으로 처리
public record MenuOption(int num, String name, String pgmName, char userType) {}

public static final List<MenuOption> MAIN_MENU_OPTIONS = List.of(
    new MenuOption(1,  "Account View",              "COACTVWC", 'U'),
    new MenuOption(2,  "Account Update",            "COACTUPC", 'U'),
    new MenuOption(3,  "Credit Card List",           "COCRDLIC", 'U'),
    new MenuOption(4,  "Credit Card View",           "COCRDSLC", 'U'),
    new MenuOption(5,  "Credit Card Update",         "COCRDUPC", 'U'),
    new MenuOption(6,  "Transaction List",           "COTRN00C", 'U'),
    new MenuOption(7,  "Transaction View",           "COTRN01C", 'U'),
    new MenuOption(8,  "Transaction Add",            "COTRN02C", 'U'),
    new MenuOption(9,  "Transaction Reports",        "CORPT00C", 'U'),
    new MenuOption(10, "Bill Payment",               "COBIL00C", 'U'),
    new MenuOption(11, "Pending Authorization View", "COPAUS0C", 'U')
);

// COBOL: CDEMO-MENU-OPT-COUNT VALUE 11 → List.size()로 대체
public static final int MENU_OPT_COUNT = MAIN_MENU_OPTIONS.size();
```

### 2. 데이터 주도 라우팅 → `Map<String, Supplier<Controller>>`

COMEN01C가 `CDEMO-MENU-OPT-PGMNAME`을 그대로 XCTL 대상으로 넘기는 패턴은 Java에서 `Map`으로 구현한다.

```java
// COBOL: EXEC CICS XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPT-NUM))
// Java: Spring MVC 라우팅 또는 명시적 핸들러 맵
Map<String, String> pgmToRoute = Map.of(
    "COACTVWC", "/accounts/view",
    "COPAUS0C", "/auth/pending",
    // ...
);
```

### 3. 선택적 모듈(옵션 11) → 피처 플래그(Feature Flag)

`COPAUS0C`(Pending Authorization View)는 IMS/DB2/MQ 선택 모듈이다. COBOL에서는 `EXEC CICS INQUIRE PROGRAM`으로 런타임에 설치 여부를 확인한다. Java/Spring에서는 피처 플래그 또는 조건부 Bean 등록으로 동일 효과를 낸다.

```java
// application.yml
features:
  pending-auth: ${PENDING_AUTH_MODULE_ENABLED:false}

// Controller
@ConditionalOnProperty("features.pending-auth")
@RestController("/auth/pending")
public class PendingAuthController { ... }
```

### 4. FILLER 블록의 바이트 레이아웃 주의사항

COBOL 소스에서 각 FILLER는 `PIC 9(02)`(DISPLAY 형식, 2바이트 문자형 숫자)로 선언되어 있다. Java 마이그레이션 시 이 값을 `int`로 읽으려면 EBCDIC → ASCII 변환 후 `Integer.parseInt()`를 사용해야 한다. COMP(바이너리) 형식이 **아님**에 주의한다.

### 5. 1-based 인덱싱 → 0-based

COBOL OCCURS는 인덱스 1부터 시작한다(`CDEMO-MENU-OPT(1)` ~ `CDEMO-MENU-OPT(11)`). Java `List`는 0-based이므로 `get(index - 1)` 또는 인덱스 변환 로직이 필요하다.

### 6. OCCURS 12 vs 데이터 11개 불일치

OCCURS 12개를 예약했지만 FILLER 데이터는 11개분(506바이트)만 있어 슬롯 12는 `CDEMO-MENU-OPTIONS-DATA` 범위를 벗어난다. Java `List`는 이 문제를 원천 차단한다(`List.of(...)`는 정확히 11개). COBOL 원본의 `OCCURS 12` 선언이 미래 확장을 위한 여유 슬롯인지, 단순 오류인지는 불명확하다(추측).
