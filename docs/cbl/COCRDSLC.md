# COCRDSLC — 신용카드 상세 조회 화면 (카드 단건 검색)

- **유형**: CICS 온라인 COBOL (Pseudo-Conversational)
- **한 줄 요약**: 계좌번호 + 카드번호를 입력받아 CARDDAT VSAM KSDS에서 단건 카드 레코드를 조회하고 결과를 화면에 표시하는 카드 상세 조회 프로그램

---

## 기능 설명

COCRDSLC는 CardDemo 온라인 시스템의 카드 상세 조회 화면(`CCDL` 트랜잭션)을 처리한다. 사용자가 계좌번호(11자리)와 카드번호(16자리)를 입력하면 VSAM KSDS 파일 `CARDDAT`에서 카드 레코드를 직접 키 조회(EXEC CICS READ)하여 카드 소지자명, 만료 연/월, 카드 상태 코드를 화면에 표시한다.

두 가지 진입 경로를 지원한다.

1. **카드 목록 화면(COCRDLIC)에서 선택 항목을 전달받아 진입**: commarea에 계좌번호·카드번호가 이미 담겨 있으므로 입력 검증 없이 즉시 파일을 조회한다(라인 339–348).
2. **다른 화면 또는 메뉴에서 직접 진입**: 빈 입력 화면을 먼저 표시하고, 사용자가 값을 입력·제출하면 재진입(REENTER) 경로에서 입력 검증 후 파일을 조회한다(라인 357–370).

PF3 키를 누르면 호출 프로그램(보통 COCRDLIC 또는 메인 메뉴 COMEN01C)으로 XCTL하여 돌아간다.

---

## 입력 / 출력

- **입력**:
  - 사용자 키 입력: 화면 필드 `ACCTSIDI`(계좌번호 PIC X(11)), `CARDSIDI`(카드번호 PIC X(16))
  - DFHCOMMAREA: `CARDDEMO-COMMAREA`(COCOM01Y) + `WS-THIS-PROGCOMMAREA`(CA-FROM-PROGRAM, CA-FROM-TRANID) 연결 구조
  - VSAM KSDS 파일 `CARDDAT` — 기본 키 카드번호(16자리)로 단건 READ
  - VSAM KSDS 파일 `CARDAIX` — 계좌번호 AIX(Alternative Index)를 통한 READ (9150-GETCARD-BYACCT 단락, 현재 호출되지 않으나 코드에 존재)

- **출력**:
  - BMS 화면 `CCRDSLA`(맵셋 `COCRDSL`): 카드 소지자명(`CRDNAMEO`), 만료월(`EXPMONO`), 만료연(`EXPYEARO`), 카드 상태 코드(`CRDSTCDO`), 오류 메시지(`ERRMSGO`), 안내 메시지(`INFOMSGO`)
  - 다음 트랜잭션 `CCDL`로 `EXEC CICS RETURN` 시 commarea 전달 (상태 유지)

---

## 의존성

- **COPY (카피북)**:

  | 카피북 | 경로 | 역할 |
  |---|---|---|
  | `CVCRD01Y` | app/cpy/CVCRD01Y.cpy | `CC-WORK-AREA` — CCARD-AID(키 플래그), CC-ACCT-ID/CC-CARD-NUM 작업 필드 |
  | `COCOM01Y` | app/cpy/COCOM01Y.cpy | `CARDDEMO-COMMAREA` — 프로그램 간 세션 상태(commarea DTO) |
  | `DFHBMSCA` | IBM 제공 | BMS 화면 속성 상수(DFHBMPRF=보호, DFHBMFSE=활성, DFHRED, DFHDFCOL 등) |
  | `DFHAID` | IBM 제공 | EIB AID 코드 상수(DFHENTER, DFHPF3 등) |
  | `COTTL01Y` | app/cpy/ | 화면 공통 타이틀 문자열(CCDA-TITLE01, CCDA-TITLE02) |
  | `COCRDSL` | app/cpy-bms/COCRDSL.CPY | BMS 심볼릭 맵 — `CCRDSLAI`(입력구조) / `CCRDSLAO`(출력구조, REDEFINES) |
  | `CSDAT01Y` | app/cpy/ | 현재 날짜/시간 작업 필드(WS-CURDATE-DATA 등) |
  | `CSMSG01Y` | app/cpy/ | 공통 메시지 상수 |
  | `CSMSG02Y` | app/cpy/ | Abend 변수(ABEND-DATA, ABEND-CULPRIT, ABEND-CODE 등) |
  | `CSUSR01Y` | app/cpy/ | 로그인 사용자 정보 |
  | `CVACT02Y` | app/cpy/CVACT02Y.cpy | `CARD-RECORD` — CARDDAT 파일 레코드 레이아웃(150바이트) |
  | `CVCUS01Y` | app/cpy/ | `CUSTOMER-RECORD` (이 프로그램에서 직접 참조하지는 않으나 선언됨) |
  | `CSSTRPFY` | app/cpy/ | PF 키 저장 공통 로직(`COPY 'CSSTRPFY'`, 라인 855) |

- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)` — PF3 시 호출 프로그램(`COCRDLIC` 또는 `COMEN01C`)으로 제어 이양 (라인 331–334)
  - COMEN01C(`LIT-MENUPGM = 'COMEN01C'`) — XCTL 기본 복귀 대상(메인 메뉴)
  - COCRDLIC(`LIT-CCLISTPGM = 'COCRDLIC'`) — 목록 화면. XCTL 복귀 대상 및 진입 경로 식별에 사용

- **데이터셋/파일/DB 테이블**:
  - `CARDDAT` (LIT-CARDFILENAME, 라인 188) — VSAM KSDS, 기본 키 = 카드번호(16자리), 레코드 150바이트 (`CARD-RECORD`)
  - `CARDAIX` (LIT-CARDFILENAME-ACCT-PATH, 라인 190) — CARDDAT의 AIX(계좌번호 키), 단락 9150에서만 사용(현재 미호출)

- **트랜잭션 ID 또는 EXEC PGM**:
  - 트랜잭션 ID: `CCDL` (LIT-THISTRANID, 라인 166)
  - 맵셋/맵: `COCRDSL` / `CCRDSLA`

---

## 핵심 로직 흐름

### 전체 제어 흐름 (0000-MAIN)

```
[CICS 기동]
  │
  ├─ EXEC CICS HANDLE ABEND LABEL(ABEND-ROUTINE)  ← 전역 예외 핸들러 등록
  ├─ INITIALIZE 작업 영역
  ├─ COMMAREA 복원: EIBCALEN=0이거나 메뉴(COMEN01C) 최초 진입 → 초기화
  │                 그 외 → DFHCOMMAREA → CARDDEMO-COMMAREA + WS-THIS-PROGCOMMAREA 복원
  ├─ PERFORM YYYY-STORE-PFKEY  ← EIBAID 값을 CCARD-AID 문자열로 매핑
  ├─ PFK 유효성 판정: Enter 또는 PF3만 허용, 나머지는 Enter로 강제 설정
  │
  └─ EVALUATE TRUE
       WHEN CCARD-AID-PFK03            ─── PF3: 이전 화면으로 XCTL 복귀
       WHEN CDEMO-PGM-ENTER AND         ─── 카드 목록에서 선택 진입
            CDEMO-FROM-PROGRAM = COCRDLIC     → 9000-READ-DATA → 1000-SEND-MAP
       WHEN CDEMO-PGM-ENTER             ─── 기타 최초 진입
            (다른 경로)                       → 1000-SEND-MAP (빈 화면 표시)
       WHEN CDEMO-PGM-REENTER           ─── 사용자 입력 후 재진입
                                             → 2000-PROCESS-INPUTS
                                             → INPUT-ERROR? → 1000-SEND-MAP
                                             → OK?          → 9000-READ-DATA → 1000-SEND-MAP
       WHEN OTHER                        ─── 예외 → SEND-PLAIN-TEXT 후 종료
```

### 1000-SEND-MAP (화면 전송)

```
1100-SCREEN-INIT       : 화면 버퍼 초기화, CURRENT-DATE로 날짜/시간 채움
1200-SETUP-SCREEN-VARS : 계좌·카드번호, 카드 소지자명, 만료 연/월, 상태 코드 화면 필드에 복사
1300-SETUP-SCREEN-ATTRS: 필드 보호/활성, 커서 위치, 색상(DFHRED = 오류) 설정
1400-SEND-SCREEN       : EXEC CICS SEND MAP ERASE FREEKB → 화면 출력
```

`1300-SETUP-SCREEN-ATTRS`의 핵심 분기:
- COCRDLIC(목록 화면)에서 온 경우 → `ACCTSIDA`, `CARDSIDA`에 `DFHBMPRF`(PROTECTED) 설정 — 입력 필드를 잠금
- 그 외 → `DFHBMFSE`(FSET) — 입력 가능
- 계좌/카드 플래그가 NOT-OK이면 해당 필드를 `DFHRED`(빨간색)로 표시

### 2000-PROCESS-INPUTS (입력 처리, REENTER 경로)

```
2100-RECEIVE-MAP       : EXEC CICS RECEIVE MAP → CCRDSLAI로 사용자 입력 수신
2200-EDIT-MAP-INPUTS   :
  ├─ '*' 또는 공백 입력은 LOW-VALUES(빈값)로 정규화
  ├─ 2210-EDIT-ACCOUNT : CC-ACCT-ID 검증
  │    └─ 미입력 → FLG-ACCTFILTER-BLANK, INPUT-ERROR 설정, GO TO exit
  │    └─ 비숫자  → FLG-ACCTFILTER-NOT-OK, INPUT-ERROR 설정, GO TO exit
  │    └─ 정상   → CDEMO-ACCT-ID에 저장, FLG-ACCTFILTER-ISVALID
  └─ 2220-EDIT-CARD    : CC-CARD-NUM 검증 (동일 패턴)
       └─ 교차검증: 계좌+카드 모두 빈칸 → NO-SEARCH-CRITERIA-RECEIVED
```

### 9000-READ-DATA (VSAM 파일 조회)

```
9100-GETCARD-BYACCTCARD:
  MOVE CC-CARD-NUM → WS-CARD-RID-CARDNUM (16자리 기본 키)
  EXEC CICS READ FILE('CARDDAT') RIDFLD(WS-CARD-RID-CARDNUM) INTO(CARD-RECORD)
  │
  ├─ DFHRESP(NORMAL)  → FOUND-CARDS-FOR-ACCOUNT = TRUE
  │                      (1200-SETUP-SCREEN-VARS에서 화면 필드 채움)
  ├─ DFHRESP(NOTFND)  → INPUT-ERROR, FLG-ACCTFILTER-NOT-OK, FLG-CARDFILTER-NOT-OK
  │                      WS-RETURN-MSG = 'Did not find cards for this search condition'
  └─ 기타 오류        → INPUT-ERROR, WS-FILE-ERROR-MESSAGE 포맷팅 후 WS-RETURN-MSG에 저장
```

> 주의: 9150-GETCARD-BYACCT(CARDAIX AIX 조회) 단락은 소스에 존재하지만 현재 `PERFORM`으로 호출되지 않는다(라인 779–811). 계좌번호만으로 조회하는 기능이 미완성 상태이거나 의도적으로 비활성화된 것으로 보인다.

### COMMON-RETURN (매 턴 종료)

```
WS-RETURN-MSG → CCARD-ERROR-MSG
CARDDEMO-COMMAREA + WS-THIS-PROGCOMMAREA → WS-COMMAREA(2000바이트)로 직렬화
EXEC CICS RETURN TRANSID('CCDL') COMMAREA(WS-COMMAREA)
```

HTTP 비교: Stateless HTTP 응답에 세션 쿠키를 붙이는 것과 동일. 다음 키 입력 시 같은 트랜잭션 `CCDL`이 재기동되어 commarea를 복원한다.

---

## Java/현대화 노트

### 1. Pseudo-Conversational 패턴 → Spring MVC 또는 REST

COBOL의 `EXEC CICS RETURN TRANSID ... COMMAREA`는 다음 요청까지 서버 메모리를 해제하는 방식이다. Java 현대화 시 두 가지 매핑이 가능하다.

```java
// 방법 A: Spring MVC + 세션 (commarea → HttpSession)
@GetMapping("/card/detail")
public String showCardDetail(
        @RequestParam(required = false) String acctId,
        @RequestParam(required = false) String cardNum,
        Model model, HttpSession session) {
    CardDemoSession ctx = (CardDemoSession) session.getAttribute("CARDDEMO-COMMAREA");
    // CDEMO-PGM-REENTER 상태에 따라 분기
}

// 방법 B: REST API (commarea → JWT/토큰)
@GetMapping("/api/cards/{cardNum}")
public ResponseEntity<CardDetailDto> getCardDetail(@PathVariable String cardNum) { ... }
```

### 2. COMMAREA 이중 구조

소스에서 commarea는 `CARDDEMO-COMMAREA`(공용, COCOM01Y)와 `WS-THIS-PROGCOMMAREA`(이 프로그램 전용, CA-FROM-PROGRAM + CA-FROM-TRANID)를 연접하여 `WS-COMMAREA(2000)`에 담는다(라인 397–400).

```cobol
MOVE CARDDEMO-COMMAREA TO WS-COMMAREA
MOVE WS-THIS-PROGCOMMAREA TO
     WS-COMMAREA(LENGTH OF CARDDEMO-COMMAREA + 1: ...)
```

Java로는 하나의 세션 DTO로 통합하면 된다.

```java
public class CardDemoSession implements Serializable {
    private CardDemoCommonArea commonArea;   // CARDDEMO-COMMAREA
    private String fromProgram;             // CA-FROM-PROGRAM
    private String fromTranId;              // CA-FROM-TRANID
}
```

### 3. REDEFINES — 숫자/문자 듀얼 뷰

`CARD-ACCT-ID-X`(PIC X(11)) + `CARD-ACCT-ID-N REDEFINES`(PIC 9(11))처럼 같은 버퍼를 문자열과 숫자로 교대로 접근하는 패턴이 반복된다. Java에는 직접 대응 구문이 없다.

```java
// COBOL REDEFINES 패턴 → Java 변환 유틸리티 메서드로 대체
String acctIdX = "00000000123"; // PIC X(11)
long  acctIdN  = Long.parseLong(acctIdX.trim()); // PIC 9(11)

// 숫자 여부 검증 (IS NUMERIC 상당)
boolean isNumeric = acctIdX.matches("\\d{11}");
```

### 4. 카드 레코드 매핑 (CVACT02Y → Java DTO)

```cobol
01  CARD-RECORD.
    05  CARD-NUM              PIC X(16)   -- 카드번호 (기본 키)
    05  CARD-ACCT-ID          PIC 9(11)   -- 연결 계좌번호
    05  CARD-CVV-CD           PIC 9(03)   -- CVV
    05  CARD-EMBOSSED-NAME    PIC X(50)   -- 카드 소지자명
    05  CARD-EXPIRAION-DATE   PIC X(10)   -- 만료일 (YYYY-MM-DD 형식)
    05  CARD-ACTIVE-STATUS    PIC X(01)   -- 상태 코드 (예: 'Y'/'N')
    05  FILLER                PIC X(59)   -- 패딩
```

```java
public class CardRecord {
    private String cardNum;           // PIC X(16), 고정 16자리 문자열
    private long   cardAcctId;        // PIC 9(11), 최대 11자리 양의 정수
    private int    cardCvvCd;         // PIC 9(3)
    private String cardEmbossedName;  // PIC X(50), 오른쪽 공백 트림 필요
    private String cardExpirationDate;// PIC X(10), "YYYY-MM-DD" 파싱 → LocalDate
    private String cardActiveStatus;  // PIC X(1), Enum화 권장
}
```

> **주의**: CARD-EXPIRAION-DATE는 오타(`EXPIRAION`, EXPIRATION이 맞음)이며 소스 전체에 동일 오타가 사용된다. Java 변환 시 올바른 이름으로 교정해야 한다.

### 5. BMS 심볼릭 맵 → HTML Form / DTO

`COCRDSL.CPY`의 `CCRDSLAI`/`CCRDSLAO`는 BMS가 자동 생성한 화면 입출력 구조체다. 각 논리 필드(예: `ACCTSID`)마다 길이(L), 속성(F/A), 값(I/O), 색상(C) 서브필드가 함께 존재한다. Java 현대화 시 값 필드만 추출하여 DTO로 사용하면 된다.

```java
public class CrdSlDetailForm {
    @Size(min=11, max=11) @Pattern(regexp="\\d{11}")
    private String acctId;   // ACCTSIDI PIC X(11)

    @Size(min=16, max=16) @Pattern(regexp="\\d{16}")
    private String cardNum;  // CARDSIDI PIC X(16)

    // 출력 전용
    private String cardName;   // CRDNAMEO PIC X(50)
    private String expMonth;   // EXPMONO  PIC X(2)
    private String expYear;    // EXPYEARO PIC X(4)
    private String cardStatus; // CRDSTCDO PIC X(1)
    private String errorMsg;   // ERRMSGO  PIC X(80)
    private String infoMsg;    // INFOMSGO PIC X(40)
}
```

### 6. 필드 색상/보호 속성 → UI 검증 피드백

COBOL에서 `DFHRED`(빨간색), `DFHBMPRF`(보호)로 오류를 표시하는 로직은 Java 웹 계층에서 Spring Validation + Thymeleaf/React의 필드별 오류 메시지 및 `readonly` 속성으로 대응한다.

```java
// 입력 오류 시 필드 하이라이트 (DFHRED 상당)
model.addAttribute("acctIdError", "Account number must be a non zero 11 digit number");

// 목록 화면에서 온 경우 필드 잠금 (DFHBMPRF 상당)
model.addAttribute("fieldsReadOnly", fromCocrdlic);
```

### 7. 입력 검증 패턴 (EDIT 단락)

2210/2220 단락의 검증은 Java Bean Validation으로 직접 대체 가능하다.

| COBOL 조건 | Java 상당 |
|---|---|
| `CC-ACCT-ID EQUAL LOW-VALUES OR SPACES OR ZEROS` | `@NotBlank`, `@Min(1)` |
| `CC-ACCT-ID IS NOT NUMERIC` | `@Pattern(regexp="\\d{11}")` |
| `CC-CARD-NUM IS NOT NUMERIC` | `@Pattern(regexp="\\d{16}")` |
| 양쪽 모두 미입력 | 클래스 레벨 `@AssertTrue` 또는 커스텀 `@Constraint` |

### 8. 데이터베이스 전환 시 고려사항

현재 CARDDAT는 VSAM KSDS(카드번호 기본 키) + CARDAIX AIX(계좌번호 보조 키)로 구성된다. RDB 전환 시:

```sql
CREATE TABLE CARD (
    CARD_NUM       CHAR(16)    NOT NULL PRIMARY KEY,
    CARD_ACCT_ID   NUMERIC(11) NOT NULL,
    CARD_CVV_CD    NUMERIC(3),
    CARD_EMBO_NAME VARCHAR(50),
    CARD_EXP_DATE  DATE,
    CARD_STATUS    CHAR(1),
    FOREIGN KEY (CARD_ACCT_ID) REFERENCES ACCOUNT(ACCT_ID)
);
CREATE INDEX IDX_CARD_ACCT ON CARD(CARD_ACCT_ID); -- CARDAIX AIX 상당
```

### 9. 미사용 코드 (9150-GETCARD-BYACCT)

9150-GETCARD-BYACCT 단락은 `CARDAIX`(계좌번호 AIX) 경로로 카드를 조회하는 로직이나, 현재 어떤 `PERFORM` 호출도 없다. Java 전환 시 이 경로를 `findCardByAccountId()` 메서드로 구현할지 여부를 비즈니스 담당자에게 확인해야 한다.

### 10. 필드명 오타

소스 전반에 `CARD-EXPIRAION-DATE`(EXPIRATION → EXPIRAION) 오타가 일관되게 사용된다. CVACT02Y, COCRDSLC 양쪽에 동일하게 존재하므로, Java 마이그레이션 시 한 번에 교정해야 한다.
