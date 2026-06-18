# COTRN01C — 거래 상세 조회 (단건 View)

- **유형**: CICS 온라인 COBOL
- **한 줄 요약**: 사용자가 거래 ID를 입력하면 TRANSACT KSDS 파일에서 해당 거래 레코드를 단건 조회하여 화면에 표시하는 CICS pseudo-conversational 프로그램.

---

## 기능 설명

COTRN01C는 CardDemo 애플리케이션의 거래 상세 조회 화면(COTRN1A)을 제어한다. 사용자가 16자리 거래 ID(TRNIDINI)를 입력하고 Enter를 누르면 TRANSACT VSAM KSDS 파일을 키 기반으로 단건 읽기(EXEC CICS READ ... UPDATE)하여 거래의 모든 속성—유형 코드, 카테고리 코드, 출처, 설명, 금액, 가맹점 정보, 원거래/처리 타임스탬프—을 화면 필드에 채워 표시한다.

Java/Spring MVC 관점에서 보면 `GET /transactions/{id}` 엔드포인트가 DB에서 단건을 조회해 View에 바인딩하는 구조와 동형이다. CICS pseudo-conversational 방식이므로 `EXEC CICS RETURN TRANSID('CT01') COMMAREA(...)` 호출로 매 사용자 응답 사이에 프로그램은 종료되며, 다음 키 입력 시 트랜잭션 ID `CT01`로 재기동된다(HTTP stateless + session token 직렬화와 동일한 패턴).

COTRN00C(거래 목록 화면)에서 선택된 거래 ID가 commarea의 `CDEMO-CT01-TRN-SELECTED` 필드를 통해 전달되면, 이 프로그램은 최초 진입 시 자동으로 해당 거래를 조회하여 표시한다(라인 103~108).

---

## 입력 / 출력

### 입력
| 구분 | 이름 | 설명 |
|---|---|---|
| CICS AID 키 | EIBAID | DFHENTER(조회 실행), DFHPF3(이전 화면), DFHPF4(화면 초기화), DFHPF5(COTRN00C 이동) |
| 화면 입력 필드 | `TRNIDINI OF COTRN1AI` | 사용자가 입력한 거래 ID (PIC X(16)) |
| COMMAREA | `CARDDEMO-COMMAREA` | 이전 화면에서 전달된 세션 상태 (`CDEMO-CT01-TRN-SELECTED` 포함) |
| EIBCALEN | 시스템 EIB 필드 | COMMAREA 유무 판별 (0이면 최초 진입) |

### 출력
| 구분 | 이름 | 설명 |
|---|---|---|
| BMS 화면 | `COTRN1AO` (맵셋 COTRN01, 맵 COTRN1A) | 거래 상세 정보가 채워진 화면 |
| COMMAREA | `CARDDEMO-COMMAREA` | `EXEC CICS RETURN`으로 다음 턴에 전달되는 세션 상태 |
| 에러 메시지 | `ERRMSGO OF COTRN1AO` | 입력 오류·파일 오류 시 화면 하단 메시지 라인 |

---

## 의존성

### COPY (카피북)
| Copybook | 위치 | 역할 |
|---|---|---|
| `COCOM01Y` | `app/cpy/COCOM01Y.cpy` | `CARDDEMO-COMMAREA` 구조체 (세션 상태 객체) |
| `COTRN01` (BMS) | `app/cpy-bms/COTRN01.CPY` | 화면 symbolic map (`COTRN1AI` 입력, `COTRN1AO` 출력, REDEFINES) |
| `COTTL01Y` | `app/cpy/COTTL01Y.cpy` | 화면 타이틀 상수 (`CCDA-TITLE01`, `CCDA-TITLE02`) |
| `CSDAT01Y` | `app/cpy/CSDAT01Y.cpy` | 현재 날짜/시간 포맷 구조체 (`WS-CURDATE-DATA` 등) |
| `CSMSG01Y` | `app/cpy/CSMSG01Y.cpy` | 공통 메시지 상수 (`CCDA-MSG-INVALID-KEY` 등) |
| `CVTRA05Y` | `app/cpy/CVTRA05Y.cpy` | `TRAN-RECORD` 레이아웃 (TRANSACT 파일, RECLN=350) |
| `DFHAID` | CICS 시스템 | AID 상수 (DFHENTER, DFHPF3, DFHPF4, DFHPF5 등) |
| `DFHBMSCA` | CICS 시스템 | BMS 화면 속성 상수 |

### 호출 프로그램 (CALL/XCTL/LINK)
| 방식 | 대상 프로그램 | 조건 |
|---|---|---|
| `EXEC CICS XCTL` | `COSGN00C` | EIBCALEN=0(최초 진입) 또는 CDEMO-TO-PROGRAM 미설정 시 로그인 화면으로 |
| `EXEC CICS XCTL` | `CDEMO-FROM-PROGRAM` (동적) | PF3 압력 시 이전 화면으로 복귀 (기본값 `COMEN01C`) |
| `EXEC CICS XCTL` | `COTRN00C` | PF5 압력 시 거래 목록 화면으로 이동 |

### 데이터셋/파일/DB 테이블
| 파일명 | 구조 | 접근 방식 |
|---|---|---|
| `TRANSACT` | VSAM KSDS, RECLN=350, 키=`TRAN-ID` PIC X(16) | `EXEC CICS READ ... RIDFLD(TRAN-ID) UPDATE` (단건 키 조회, 라인 269~278) |

> **주의**: `UPDATE` 옵션이 붙어 있으나 이 프로그램에서는 실제로 레코드를 수정하거나 REWRITE/UNLOCK을 수행하지 않는다. READ UPDATE로 획득된 exclusive enqueue는 다음 `EXEC CICS RETURN` 시 CICS가 자동으로 해제한다. 이는 CardDemo 전반의 관습적 코딩 패턴으로 보인다(추측). Java로 마이그레이션 시 불필요한 잠금이므로 단순 읽기 조회로 변경해야 한다.

### 트랜잭션 ID 또는 EXEC PGM
- **CICS 트랜잭션 ID**: `CT01` (WS-TRANID, 라인 37)
- EXEC PGM 없음 (배치 프로그램 아님)

---

## 핵심 로직 흐름

```
MAIN-PARA
│
├─ [초기화] ERR-FLG-OFF, USR-MODIFIED-NO, WS-MESSAGE·ERRMSGO → SPACES
│
├─ IF EIBCALEN = 0          ← COMMAREA 없음 = 다른 트랜잭션에서 직접 호출된 것이 아님
│   └─ XCTL → COSGN00C     ← 로그인 화면으로 강제 이동
│
└─ ELSE  (정상 진입: commarea 있음)
    MOVE DFHCOMMAREA → CARDDEMO-COMMAREA
    │
    ├─ IF NOT CDEMO-PGM-REENTER  ← 이 화면에 처음 진입 (CDEMO-PGM-CONTEXT = 0)
    │   SET CDEMO-PGM-REENTER TO TRUE   ← 다음 턴은 REENTER로 처리
    │   MOVE LOW-VALUES → COTRN1AO      ← 출력 화면 초기화
    │   MOVE -1 → TRNIDINL              ← 커서를 거래 ID 입력 필드에 위치
    │   │
    │   ├─ IF CDEMO-CT01-TRN-SELECTED ≠ SPACES/LOW-VALUES
    │   │   ← COTRN00C에서 선택한 거래 ID가 commarea에 있음
    │   │   MOVE CDEMO-CT01-TRN-SELECTED → TRNIDINI
    │   │   PERFORM PROCESS-ENTER-KEY   ← 자동 조회 실행
    │   └─ PERFORM SEND-TRNVIEW-SCREEN
    │
    └─ ELSE  ← REENTER: 사용자가 이미 이 화면을 보고 있음
        PERFORM RECEIVE-TRNVIEW-SCREEN  ← 사용자 입력 수신
        EVALUATE EIBAID
            WHEN DFHENTER  → PERFORM PROCESS-ENTER-KEY
            WHEN DFHPF3    → XCTL 이전 화면 (CDEMO-FROM-PROGRAM 또는 COMEN01C)
            WHEN DFHPF4    → PERFORM CLEAR-CURRENT-SCREEN (화면 초기화)
            WHEN DFHPF5    → XCTL COTRN00C (거래 목록)
            WHEN OTHER     → ERR-FLG-ON, "Invalid key" 메시지, 화면 재전송

EXEC CICS RETURN TRANSID('CT01') COMMAREA(CARDDEMO-COMMAREA)
← 매 턴 마지막에 실행. 다음 키 입력까지 프로그램 종료.
```

### PROCESS-ENTER-KEY 세부 흐름

```
PROCESS-ENTER-KEY
│
├─ IF TRNIDINI = SPACES/LOW-VALUES
│   ERR-FLG-ON, "Tran ID can NOT be empty..." → SEND-TRNVIEW-SCREEN
│   (이후 로직 IF NOT ERR-FLG-ON 조건으로 건너뜀)
│
└─ ELSE CONTINUE  (입력값 있음)
    │
    └─ IF NOT ERR-FLG-ON
        MOVE SPACES → 화면의 모든 출력 필드 초기화 (13개 필드)
        MOVE TRNIDINI → TRAN-ID          ← 검색 키 세팅
        PERFORM READ-TRANSACT-FILE       ← VSAM KSDS 단건 조회
    │
    └─ IF NOT ERR-FLG-ON  (조회 성공)
        TRAN-RECORD 각 필드 → COTRN1AI 화면 필드 매핑:
          TRAN-ID         → TRNIDI
          TRAN-CARD-NUM   → CARDNUMI
          TRAN-TYPE-CD    → TTYPCDI
          TRAN-CAT-CD     → TCATCDI
          TRAN-SOURCE     → TRNSRCI
          TRAN-AMT        → WS-TRAN-AMT (편집 포맷) → TRNAMTI
          TRAN-DESC       → TDESCI
          TRAN-ORIG-TS    → TORIGDTI
          TRAN-PROC-TS    → TPROCDTI
          TRAN-MERCHANT-ID   → MIDI
          TRAN-MERCHANT-NAME → MNAMEI
          TRAN-MERCHANT-CITY → MCITYI
          TRAN-MERCHANT-ZIP  → MZIPI
        PERFORM SEND-TRNVIEW-SCREEN
```

### READ-TRANSACT-FILE 세부 흐름

```
READ-TRANSACT-FILE
│
EXEC CICS READ
    DATASET('TRANSACT')
    INTO(TRAN-RECORD)        ← CVTRA05Y의 01레벨 구조체로 직접 수신
    RIDFLD(TRAN-ID)          ← PIC X(16) 키
    KEYLENGTH(LENGTH OF TRAN-ID)
    UPDATE                   ← exclusive enqueue (실제 수정 없음, 주의사항 참조)
    RESP(WS-RESP-CD)
    RESP2(WS-REAS-CD)

EVALUATE WS-RESP-CD
    WHEN DFHRESP(NORMAL)  → CONTINUE (성공)
    WHEN DFHRESP(NOTFND)  → ERR-FLG-ON, "Transaction ID NOT found..."
    WHEN OTHER            → DISPLAY RESP/REAS, ERR-FLG-ON, "Unable to lookup Transaction..."
```

### 금액 편집 처리

- `TRAN-AMT`는 `PIC S9(09)V99` (부호 있는 9자리 정수부 + 2자리 소수부, DISPLAY 형식, 총 11바이트)
- `WS-TRAN-AMT`는 `PIC +99999999.99` (편집 그림 문자, 부호 + 앞에 표시, 소수점 명시, 12자리 표시)
- `MOVE TRAN-AMT TO WS-TRAN-AMT` 한 줄로 묵시적 소수점(V) → 명시적 소수점(.) 변환 및 부호 처리가 이루어진다(라인 177)

---

## Java/현대화 노트

### 1. pseudo-conversational = HTTP stateless + 세션

```java
// CICS RETURN TRANSID + COMMAREA  ≈  아래 패턴
@GetMapping("/transactions/{id}")
public String viewTransaction(@PathVariable String id,
                              HttpSession session,
                              Model model) {
    // COMMAREA에서 읽던 세션 상태를 HttpSession에서 복원
    CardDemoSession ctx = (CardDemoSession) session.getAttribute("ctx");
    TransactionRecord tran = transactRepository.findById(id)
            .orElseThrow(() -> new TransactionNotFoundException(id));
    model.addAttribute("tran", tran);
    return "transactionView";
}
```

### 2. TRAN-RECORD → Java DTO 매핑

CVTRA05Y(`app/cpy/CVTRA05Y.cpy`)의 레이아웃:

| COBOL 필드 | PIC | Java 타입 | 비고 |
|---|---|---|---|
| `TRAN-ID` | X(16) | `String` (16자 고정) | KSDS Primary Key |
| `TRAN-TYPE-CD` | X(02) | `String` (2자) | 거래 유형 코드 |
| `TRAN-CAT-CD` | 9(04) | `int` | 거래 카테고리 코드 |
| `TRAN-SOURCE` | X(10) | `String` | 거래 출처 |
| `TRAN-DESC` | X(100) | `String` | 거래 설명 |
| `TRAN-AMT` | S9(09)V99 | `BigDecimal` | **절대 `double` 사용 금지** — 금액은 BigDecimal |
| `TRAN-MERCHANT-ID` | 9(09) | `long` | 가맹점 ID |
| `TRAN-MERCHANT-NAME` | X(50) | `String` | 가맹점명 |
| `TRAN-MERCHANT-CITY` | X(50) | `String` | 가맹점 도시 |
| `TRAN-MERCHANT-ZIP` | X(10) | `String` | 가맹점 우편번호 |
| `TRAN-CARD-NUM` | X(16) | `String` | 카드 번호 |
| `TRAN-ORIG-TS` | X(26) | `String` → `LocalDateTime` | 원거래 타임스탬프 |
| `TRAN-PROC-TS` | X(26) | `String` → `LocalDateTime` | 처리 타임스탬프 |
| `FILLER` | X(20) | (무시) | 패딩 |

```java
@Value
public class TransactionRecord {
    String tranId;           // X(16)
    String tranTypeCd;       // X(02)
    int    tranCatCd;        // 9(04)
    String tranSource;       // X(10)
    String tranDesc;         // X(100)
    BigDecimal tranAmt;      // S9(09)V99 — 절대 double 금지
    long   tranMerchantId;   // 9(09)
    String tranMerchantName; // X(50)
    String tranMerchantCity; // X(50)
    String tranMerchantZip;  // X(10)
    String tranCardNum;      // X(16)
    String tranOrigTs;       // X(26) → 별도 파싱 필요
    String tranProcTs;       // X(26) → 별도 파싱 필요
}
```

### 3. READ UPDATE → 불필요한 잠금 제거

COBOL 코드(라인 275)에 `UPDATE`가 지정되어 있으나 이 프로그램은 레코드를 수정하지 않는다. CICS에서는 `EXEC CICS RETURN` 시 자동으로 잠금이 해제되지만, 조회 전용 화면에 exclusive lock을 거는 것은 동시성 성능을 저하시킨다. Java 마이그레이션 시 단순 `SELECT`(또는 Spring Data의 `findById`)로 대체해야 한다.

### 4. XCTL 내비게이션 → 프론트엔드 라우팅

| COBOL XCTL 대상 | 조건 | Java/웹 equivalent |
|---|---|---|
| `COSGN00C` | EIBCALEN=0 또는 세션 만료 | Spring Security → `/login` 리다이렉트 |
| `CDEMO-FROM-PROGRAM` | PF3 (뒤로 가기) | `history.back()` 또는 `Referer` 헤더 기반 리다이렉트 |
| `COMEN01C` | PF3이고 FROM-PROGRAM 없을 때 | 메인 메뉴 `/menu` 리다이렉트 |
| `COTRN00C` | PF5 | 거래 목록 `/transactions` 리다이렉트 |

### 5. 화면 symbolic map → View Model 이름 대응

`COTRN01.CPY`의 `COTRN1AI` / `COTRN1AO`는 동일 메모리를 REDEFINES로 공유한다. 입력용(`...I` 필드)과 출력용(`...O` 필드)이 같은 버퍼를 참조하는 union 구조다. Java에서는 별도 request DTO / response DTO로 분리하는 것이 자연스럽다.

주요 화면 필드 목록:

| 화면 필드 (입력) | 화면 필드 (출력) | 설명 |
|---|---|---|
| `TRNIDINI` (X(16)) | `TRNIDINO` | 사용자 입력 거래 ID |
| `TRNIDI` (X(16)) | `TRNIDO` | 조회된 거래 ID (표시용) |
| `CARDNUMI` (X(16)) | `CARDNUMO` | 카드 번호 |
| `TTYPCDI` (X(2)) | `TTYPCDO` | 거래 유형 코드 |
| `TCATCDI` (X(4)) | `TCATCDO` | 카테고리 코드 |
| `TRNSRCI` (X(10)) | `TRNSRCO` | 거래 출처 |
| `TDESCI` (X(60)) | `TDESCO` | 거래 설명 (화면 표시는 60자로 잘림, 원본은 100자) |
| `TRNAMTI` (X(12)) | `TRNAMTO` | 금액 (편집 형식, +99999999.99) |
| `TORIGDTI` (X(10)) | `TORIGDTO` | 원거래 타임스탬프 (10자 표시) |
| `TPROCDTI` (X(10)) | `TPROCDTO` | 처리 타임스탬프 (10자 표시) |
| `MIDI` (X(9)) | `MIDO` | 가맹점 ID |
| `MNAMEI` (X(30)) | `MNAMEO` | 가맹점명 (화면 30자, 원본 50자) |
| `MCITYI` (X(25)) | `MCITYO` | 가맹점 도시 |
| `MZIPI` (X(10)) | `MZIPO` | 가맹점 우편번호 |
| `ERRMSGI` (X(78)) | `ERRMSGO` | 에러/안내 메시지 |

> **주의**: 설명(`TDESCI` X(60))과 가맹점명(`MNAMEI` X(30))은 화면 표시를 위해 원본 필드보다 잘려서 복사된다. 라인 164~169에서 `MOVE TRAN-DESC TO TDESCI`는 COBOL의 묵시적 문자열 우측 잘림(`truncation on the right`)에 의해 X(100)→X(60)이 자동 처리된다. Java 마이그레이션 시 `substring(0, Math.min(s.length(), 60))`이 필요하다.

### 6. COMMAREA 필드 CDEMO-CT01-* 의 위치

소스 라인 53~61의 `CDEMO-CT01-INFO`는 `COPY COCOM01Y` 바로 뒤에 선언된 로컬 구조체다. 표준 `COCOM01Y`의 `CARDDEMO-COMMAREA`와 **별개**이며, WORKING-STORAGE에 독립적으로 존재한다. COTRN00C(목록 화면)와 COTRN01C(상세 화면) 간의 페이지 상태(선택된 거래 ID, 페이지 번호, 첫/마지막 거래 ID) 전달에 사용된다. Java로 치면 두 컨트롤러 간에 공유하는 `@SessionAttribute`이다.

---

*소스 버전: CardDemo_v1.0-15-g27d6c6f-68 / 2022-07-19*
