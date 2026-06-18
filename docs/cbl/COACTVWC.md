# COACTVWC — 계정 상세 조회 화면

- **유형**: CICS 온라인 COBOL (Pseudo-Conversational)
- **한 줄 요약**: 사용자가 계정 ID를 입력하면 카드 교차참조(CXACAIX) → 계정 마스터(ACCTDAT) → 고객 마스터(CUSTDAT) 순서로 읽기 전용 조회를 수행하고, 계정·고객 상세 정보를 화면(COACTVW 맵셋)에 표시하는 온라인 조회 프로그램.

---

## 기능 설명

COACTVWC는 CardDemo 애플리케이션에서 **계정 상세 조회(Account View)** 기능을 담당하는 CICS 온라인 프로그램이다. 트랜잭션 ID는 `CAVW`이며, BMS 맵셋 `COACTVW` / 맵 `CACTVWA`를 사용한다.

프로그램의 전체 흐름은 CardDemo 공통 pseudo-conversational 패턴(`COCOM01Y` commarea + `CDEMO-PGM-REENTER` 플래그)을 따른다(line 282~293 참조). 최초 진입 시에는 빈 입력 화면을 표시하고, 사용자가 계정 ID를 입력한 뒤 Enter를 누르면 재진입(REENTER)하여 유효성 검증 및 3단계 VSAM 읽기를 수행한다.

모든 VSAM 접근은 **읽기 전용** (`EXEC CICS READ`) 이며, 쓰기/갱신/삭제 동사는 전혀 사용하지 않는다.

---

## 입력 / 출력

- **입력**:
  - BMS 맵 `CACTVWA`의 계정 ID 입력 필드(`ACCTSIDI`, 11자리 숫자 문자열) — 사용자가 터미널에서 입력
  - DFHCOMMAREA (EIBCALEN 기반) — 이전 턴의 `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA` (line 282~293)
  - EIBAID — 눌린 PF 키 (Enter 또는 PF3)

- **출력**:
  - BMS 맵 `CACTVWA`를 통한 화면 표시:
    - 계정 정보: 상태(`ACSTTUSO`), 현재잔액(`ACURBALO`), 신용한도(`ACRDLIMO`), 현금한도(`ACSHLIMO`), 이번 주기 크레딧/데빗(`ACRCYCRO`/`ACRCYDBO`), 개설일/만료일/재발급일(`ADTOPENO`/`AEXPDTO`/`AREISDTO`), 그룹ID(`AADDGRPO`)
    - 고객 정보: 고객번호(`ACSTNUMO`), SSN 형식화(`ACSTSSNO`, `NNN-NN-NNNN` 형식으로 STRING 처리 line 496~504), FICO점수(`ACSTFCOO`), 생년월일(`ACSTDOBO`), 이름(`ACSFNAMO`/`ACSMNAMO`/`ACSLNAMO`), 주소, 전화번호 2개, 정부발급ID, EFT계좌, 주카드소지자 구분
  - EXEC CICS RETURN (TRANSID `CAVW`, COMMAREA 갱신) — 다음 턴을 위한 상태 보존

---

## 의존성

- **COPY (카피북)**:

  | Copybook | 용도 |
  |---|---|
  | `CVCRD01Y` | CC-WORK-AREA (카드 공통 작업 영역, line 207) |
  | `COCOM01Y` | `CARDDEMO-COMMAREA` — 세션 상태 객체 (line 211) |
  | `DFHBMSCA` | BMS 화면 속성 상수 (DFHBMFSE, DFHDFCOL, DFHRED, DFHBMDAR, DFHNEUTR 등) |
  | `DFHAID` | EIBAID PF 키 상수 (DFHENTER, DFHPF3 등) |
  | `COTTL01Y` | 화면 타이틀 (`CCDA-TITLE01`, `CCDA-TITLE02`) |
  | `COACTVW` | BMS symbolic map (`CACTVWAI`/`CACTVWAO`) |
  | `CSDAT01Y` | 현재 날짜/시간 작업 영역 (`WS-CURDATE-DATA` 등) |
  | `CSMSG01Y` | 공통 메시지 상수 |
  | `CSMSG02Y` | ABEND 작업 변수 (`ABEND-DATA`, `ABEND-CULPRIT`, `ABEND-MSG`) |
  | `CSUSR01Y` | 로그인 사용자 데이터 |
  | `CVACT01Y` | `ACCOUNT-RECORD` 레이아웃 (ACCTDAT VSAM, RECLN 300) |
  | `CVACT02Y` | 카드 레코드 레이아웃 (CARDDAT) |
  | `CVACT03Y` | `CARD-XREF-RECORD` 레이아웃 (XREF-CARD-NUM, XREF-CUST-ID, XREF-ACCT-ID, RECLN 50) |
  | `CVCUS01Y` | `CUSTOMER-RECORD` 레이아웃 (CUSTDAT, RECLN 500) |
  | `CSSTRPFY` | PF 키 저장 공통 루틴 (line 913, `YYYY-STORE-PFKEY` 단락 포함) |

- **호출 프로그램 (CALL/XCTL/LINK)**:

  | 프로그램 | 트랜잭션 | 방향 | 조건 |
  |---|---|---|---|
  | `COMEN01C` | `CM00` | XCTL (line 349~352) | PF3 누름 + `CDEMO-FROM-PROGRAM`이 미설정인 경우 메인 메뉴로 복귀 |
  | `CDEMO-FROM-PROGRAM` (가변) | `CDEMO-FROM-TRANID` | XCTL | PF3 누름 + 이전 프로그램이 설정된 경우 해당 프로그램으로 복귀 |

  > `COCRDLIC`(`CCLI`), `COCRDUPC`(`CCUP`), `COCRDSLC`(`CCDL`)는 WS-LITERALS에 리터럴로 정의(line 151~183)되어 있으나, **이 프로그램에서 직접 XCTL하지 않는다**. 호출자(메뉴)가 네비게이션에 활용할 목적으로 정의된 것으로 보인다(추측).

- **데이터셋/파일/DB 테이블**:

  | CICS DD명 | 리터럴 | 접근 유형 | 키 |
  |---|---|---|---|
  | `CXACAIX` | `LIT-CARDXREFNAME-ACCT-PATH` | READ (대체 인덱스 AIX) | `WS-CARD-RID-ACCT-ID-X` (PIC X(11)) |
  | `ACCTDAT` | `LIT-ACCTFILENAME` | READ (기본 키) | `WS-CARD-RID-ACCT-ID-X` (PIC X(11)) |
  | `CUSTDAT` | `LIT-CUSTFILENAME` | READ (기본 키) | `WS-CARD-RID-CUST-ID-X` (PIC X(09)) |

  > DB2 SQL, MQ, IMS DL/I 사용 없음. 순수 VSAM KSDS/AIX 조회만 수행.

- **트랜잭션 ID 또는 EXEC PGM**:
  - 트랜잭션 ID: **`CAVW`** (line 146, `LIT-THISTRANID`)
  - 맵셋: `COACTVW ` (8자, 후행 공백 포함, line 148)
  - 맵: `CACTVWA` (line 150)

---

## 핵심 로직 흐름

```
[CICS 진입점]
    │
    ▼
0000-MAIN
    ├─ HANDLE ABEND → ABEND-ROUTINE (line 264~266)
    ├─ INITIALIZE 작업 영역
    ├─ COMMAREA 로드: EIBCALEN=0 이면 초기화, 아니면 DFHCOMMAREA 복사 (line 282~293)
    ├─ PERFORM YYYY-STORE-PFKEY  ← COPY CSSTRPFY (PF 키 → CCARD-AID-* 플래그)
    ├─ AID 유효성 확인: Enter/PF3 이외는 Enter로 강제 처리 (line 306~314)
    │
    └─ EVALUATE TRUE
         ├─ WHEN CCARD-AID-PFK03 (PF3 누름)
         │     ├─ CDEMO-FROM-TRANID/PROGRAM 확인 → CDEMO-TO-TRANID/PROGRAM 설정
         │     └─ EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)  ← 호출자 또는 메인 메뉴로 복귀
         │
         ├─ WHEN CDEMO-PGM-ENTER (최초 진입 또는 메뉴에서 이동)
         │     └─ PERFORM 1000-SEND-MAP → GO TO COMMON-RETURN  ← 빈 입력 화면 표시
         │
         ├─ WHEN CDEMO-PGM-REENTER (사용자가 값 입력 후 Enter)
         │     ├─ PERFORM 2000-PROCESS-INPUTS
         │     │     ├─ 2100-RECEIVE-MAP: EXEC CICS RECEIVE MAP ... INTO(CACTVWAI)
         │     │     └─ 2200-EDIT-MAP-INPUTS
         │     │           ├─ '*' 또는 SPACES → LOW-VALUES로 치환 (line 628~633)
         │     │           └─ 2210-EDIT-ACCOUNT
         │     │                 ├─ LOW-VALUES/SPACES → INPUT-ERROR + FLG-ACCTFILTER-BLANK
         │     │                 └─ NOT NUMERIC 또는 ZEROES → INPUT-ERROR + FLG-ACCTFILTER-NOT-OK
         │     │
         │     ├─ IF INPUT-ERROR → 1000-SEND-MAP(오류 표시) → COMMON-RETURN
         │     └─ ELSE
         │           ├─ PERFORM 9000-READ-ACCT
         │           │     ├─ 9200-GETCARDXREF-BYACCT
         │           │     │     └─ EXEC CICS READ DATASET('CXACAIX') RIDFLD(계정ID-X)
         │           │     │           NORMAL → XREF-CUST-ID → CDEMO-CUST-ID
         │           │     │                    XREF-CARD-NUM → CDEMO-CARD-NUM
         │           │     │           NOTFND  → INPUT-ERROR + 오류 메시지 STRING 구성
         │           │     │           OTHER   → INPUT-ERROR + 파일 오류 메시지
         │           │     │
         │           │     ├─ IF FLG-ACCTFILTER-NOT-OK → EXIT (단락 탈출)
         │           │     │
         │           │     ├─ 9300-GETACCTDATA-BYACCT
         │           │     │     └─ EXEC CICS READ DATASET('ACCTDAT') RIDFLD(계정ID-X)
         │           │     │           NORMAL → FOUND-ACCT-IN-MASTER = '1'
         │           │     │           NOTFND  → INPUT-ERROR + 오류 메시지
         │           │     │           OTHER   → INPUT-ERROR + 파일 오류 메시지
         │           │     │
         │           │     ├─ IF DID-NOT-FIND-ACCT-IN-ACCTDAT → EXIT
         │           │     │
         │           │     ├─ CDEMO-CUST-ID → WS-CARD-RID-CUST-ID (line 708)
         │           │     │
         │           │     └─ 9400-GETCUSTDATA-BYCUST
         │           │           └─ EXEC CICS READ DATASET('CUSTDAT') RIDFLD(고객ID-X)
         │           │                 NORMAL → FOUND-CUST-IN-MASTER = '1'
         │           │                 NOTFND  → INPUT-ERROR + 오류 메시지
         │           │                 OTHER   → INPUT-ERROR + 파일 오류 메시지
         │           │
         │           └─ PERFORM 1000-SEND-MAP → COMMON-RETURN  ← 결과 화면 표시
         │
         └─ WHEN OTHER → ABEND-CODE '0001' + SEND-PLAIN-TEXT

COMMON-RETURN (line 394~407)
    ├─ WS-RETURN-MSG → CCARD-ERROR-MSG (오류 메시지 화면 필드)
    ├─ CARDDEMO-COMMAREA + WS-THIS-PROGCOMMAREA → WS-COMMAREA
    └─ EXEC CICS RETURN TRANSID('CAVW') COMMAREA(WS-COMMAREA)
           ← 다음 Enter 입력 시 CAVW가 재기동되어 상태 복원
```

**1000-SEND-MAP 세부 흐름** (line 416~428):
1. `1100-SCREEN-INIT`: `CACTVWAO`를 LOW-VALUES로 초기화, 현재 날짜/시간·제목·트랜잭션명·프로그램명 설정
2. `1200-SETUP-SCREEN-VARS`: 조회 결과(ACCOUNT-RECORD, CUSTOMER-RECORD 필드)를 화면 출력 필드에 MOVE. SSN은 `NNN-NN-NNNN` 형식으로 STRING 조합 (line 496~504)
3. `1300-SETUP-SCREEN-ATTRS`: 입력 필드 속성(보호/해제), 커서 위치(-1), 오류 시 DFHRED 색상 지정
4. `1400-SEND-SCREEN`: `EXEC CICS SEND MAP ... ERASE FREEKB CURSOR` + `CDEMO-PGM-REENTER` 플래그 설정 (line 581)

**ABEND-ROUTINE** (line 916~937):
- ABEND 발생 시 `ABEND-DATA`를 화면으로 SEND하고 `EXEC CICS ABEND ABCODE('9999')` 발행.

---

## Java/현대화 노트

### 1. Pseudo-Conversational → REST Controller 매핑

| COBOL 개념 | Java/Spring 상당물 |
|---|---|
| `EXEC CICS RETURN TRANSID COMMAREA` | HTTP 응답 + 세션/쿠키로 상태 보존 |
| `CDEMO-PGM-ENTER` / `CDEMO-PGM-REENTER` | GET(초기 렌더링) / POST(폼 제출 처리) |
| `EXEC CICS XCTL PROGRAM(...)` | `return "redirect:/menu"` (Spring MVC redirect) |
| `EIBCALEN = 0` 첫 진입 판별 | 세션 속성 없음 / 신규 세션 |
| `EXEC CICS RECEIVE MAP` | `@ModelAttribute` 또는 `@RequestParam`으로 폼 데이터 바인딩 |
| `EXEC CICS SEND MAP ... ERASE` | `ModelAndView` 또는 Thymeleaf 템플릿 렌더링 |

```java
// COBOL pseudo-conversational 패턴의 Spring MVC 대응 예시
@Controller
@RequestMapping("/account")
public class AccountViewController {

    // WHEN CDEMO-PGM-ENTER (최초 진입)
    @GetMapping("/view")
    public String showForm(Model model) {
        model.addAttribute("accountForm", new AccountSearchForm());
        model.addAttribute("infoMsg", "Enter or update id of account to display");
        return "account/view";  // 1000-SEND-MAP 상당
    }

    // WHEN CDEMO-PGM-REENTER (사용자 입력 후)
    @PostMapping("/view")
    public String processInput(@ModelAttribute AccountSearchForm form,
                               BindingResult result, Model model) {
        // 2200-EDIT-MAP-INPUTS 상당
        if (!StringUtils.hasText(form.getAccountId()) || form.getAccountId().equals("*")) {
            result.rejectValue("accountId", "required", "Account number not provided");
            return "account/view";
        }
        // ... 유효성 검증 후 9000-READ-ACCT 상당
        AccountViewDto dto = accountViewService.findByAccountId(form.getAccountId());
        model.addAttribute("account", dto);
        return "account/view";
    }
}
```

### 2. 3단계 조인 읽기 순서

```
CXACAIX (AIX) --[계정ID]--> CARD-XREF-RECORD
                              ↓ XREF-CUST-ID
ACCTDAT  ------[계정ID]--> ACCOUNT-RECORD
CUSTDAT  ------[고객ID]--> CUSTOMER-RECORD
```

Java 현대화 시 이 3단계 체인을 하나의 서비스 메서드로 묶고 `Optional`로 각 단계 불발(NOTFND) 처리를 권장:

```java
public AccountViewDto findByAccountId(String accountId) {
    CardXref xref = cardXrefRepository.findByAccountId(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId));   // 9200 NOTFND
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId));   // 9300 NOTFND
    Customer customer = customerRepository.findById(xref.getCustId())
        .orElseThrow(() -> new CustomerNotFoundException(xref.getCustId())); // 9400 NOTFND
    return AccountViewDto.from(xref, account, customer);
}
```

### 3. 주요 PIC 필드 → Java 타입 매핑

| COBOL 필드 | PIC | Java 타입 | 비고 |
|---|---|---|---|
| `ACCT-ID` | `PIC 9(11)` DISPLAY | `String` 또는 `long` | 키로 사용 시 String 권장 (선행 0 보존) |
| `ACCT-CURR-BAL` | `PIC S9(10)V99` DISPLAY | `BigDecimal` | `V99` = 소수점 2자리 내재, **float/double 사용 금지** |
| `ACCT-CREDIT-LIMIT` | `PIC S9(10)V99` DISPLAY | `BigDecimal` | 동일 |
| `ACCT-ACTIVE-STATUS` | `PIC X(01)` | `String` 1자 또는 `enum AccountStatus` | 값 의미는 CVACT01Y에 미정의 — 별도 확인 필요 |
| `CUST-SSN` | `PIC 9(09)` DISPLAY | `String` | 화면 표시용 `NNN-NN-NNNN` 형식화는 서비스 계층에서 처리 |
| `CUST-FICO-CREDIT-SCORE` | `PIC 9(03)` | `int` (0~999) | |
| `CUST-DOB-YYYY-MM-DD` | `PIC X(10)` | `LocalDate` (파싱 필요) | |
| `XREF-CUST-ID` | `PIC 9(09)` | `String` 또는 `int` | |
| `XREF-ACCT-ID` | `PIC 9(11)` | `String` | |

### 4. REDEFINES 주의 사항

`WS-CARD-RID-CUST-ID` (PIC 9(09))와 `WS-CARD-RID-CUST-ID-X` (PIC X(09))는 동일한 9바이트 메모리를 공유한다(line 75~80). CICS READ의 `RIDFLD`는 문자형 포인터(`-X` 버전)를 요구하므로 이중 뷰가 필요한 것이다. Java 현대화 시에는 단순히 `String accountId` 하나를 파라미터로 전달하면 되며, 별도의 REDEFINES 처리가 불필요하다.

### 5. GO TO 사용

단락 내 `GO TO 2210-EDIT-ACCOUNT-EXIT` (line 661, 676)와 `GO TO 9000-READ-ACCT-EXIT` (line 698, 705, 714)는 **조기 탈출(early return)** 패턴이다. Java로는 `return` 또는 예외 throw로 자연스럽게 표현된다. COBOL에서 이 `GO TO`들은 같은 단락 내에서만 전진하므로 스파게티 코드를 만들지 않는다.

### 6. `0000-MAIN-EXIT` 중복 정의

line 408~413에서 `0000-MAIN-EXIT.` 단락이 **두 번** 정의되어 있다. IBM Enterprise COBOL은 마지막 정의를 유효한 것으로 처리하며, 실제 동작에는 영향이 없다(추측). 현대화 시 이 중복을 제거해야 한다.

### 7. SSN 처리 주의

CUST-SSN은 `PIC 9(09)` DISPLAY 형식(line 17, CVCUS01Y)으로 저장되며, 화면 표시 시 `STRING ... '-' ... '-' ... INTO ACSTSSNO`로 `NNN-NN-NNNN` 형식화(line 496~504)한다. Java 현대화 시 SSN은 보안 요구사항에 따라 암호화 저장 및 마스킹 표시(`***-**-NNNN` 등)를 별도로 구현해야 한다.

### 8. 오류 메시지 문자열 조합 패턴

9200/9300/9400 단락에서 RESP/RESP2 값을 포함한 오류 메시지를 `STRING ... DELIMITED BY SIZE INTO WS-RETURN-MSG`로 구성한다. Java로는 `String.format("Account: %s not found in Cross ref file. Resp: %d Reas: %d", acctId, resp, resp2)`에 해당한다.

### 9. 권장 Java 패키지 구조

```
com.carddemo.account.view
  ├── AccountViewController.java      // 0000-MAIN (CICS 진입/라우팅)
  ├── AccountViewService.java         // 9000-READ-ACCT (3단계 조인)
  ├── AccountViewDto.java             // ACCOUNT-RECORD + CUSTOMER-RECORD + CARD-XREF-RECORD 통합
  ├── AccountSearchForm.java          // 2200-EDIT-MAP-INPUTS 입력 모델
  └── AccountViewValidator.java       // 2210-EDIT-ACCOUNT 유효성 검증
```
