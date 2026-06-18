# COTRN00C — 거래 목록 조회 (Transaction List)

- **유형**: CICS 온라인 COBOL
- **한 줄 요약**: VSAM TRANSACT 파일을 순차 Browse하여 한 페이지에 10건씩 거래 목록을 3270 단말에 표시하고, 사용자가 특정 거래를 선택(S)하면 COTRN01C(거래 상세)로 제어를 이전하는 pseudo-conversational 페이지네이션 프로그램이다.

---

## 기능 설명

COTRN00C는 CardDemo 애플리케이션의 거래 목록 화면을 담당한다. 사용자가 조회 시작 거래 ID를 입력(또는 빈 값으로 전체 처음부터 시작)하면 프로그램은 TRANSACT VSAM 파일을 `STARTBR`→`READNEXT`/`READPREV` 조합으로 순회하며 최대 10건을 화면에 채운다.

주요 기능은 다음과 같다.

1. **페이지 이동**: PF8(다음 페이지), PF7(이전 페이지)로 앞·뒤 방향 브라우즈가 가능하다.
2. **거래 선택**: 각 행 왼쪽의 선택 필드(SEL)에 `S`를 입력하고 Enter를 누르면 해당 거래 ID를 COMMAREA에 담아 `XCTL COTRN01C`로 전달한다.
3. **조회 시작점 필터**: 입력 필드 `TRNIDINI`(숫자 16자리)에 거래 ID를 직접 입력하면 해당 ID 이후의 레코드부터 목록을 표시한다.
4. **메뉴 복귀**: PF3을 누르면 메인 메뉴(COMEN01C)로 돌아간다.

---

## 입력 / 출력

- **입력**:
  - CICS COMMAREA (`CARDDEMO-COMMAREA`, copybook `COCOM01Y`) — 이전 프로그램에서 넘어온 컨텍스트(사용자 ID, 재진입 여부, CT00 페이지 상태 등)
  - BMS 맵 `COTRN0AI` — 단말 사용자 입력 (거래 ID 검색 키, 행 선택 플래그 SEL0001I~SEL0010I)
  - VSAM 파일 `TRANSACT` — 거래 레코드 순차 Browse (copybook `CVTRA05Y` 기준 350바이트 고정 레코드)
  - `EIBCALEN`, `EIBAID` — CICS 실행 블록(EIB) 시스템 필드

- **출력**:
  - BMS 맵 `COTRN0AO` — 10행 거래 목록(ID, 날짜, 설명, 금액) + 헤더(날짜/시간/페이지 번호) + 오류 메시지
  - CICS COMMAREA — 다음 호출을 위해 `CDEMO-CT00-TRNID-FIRST`, `CDEMO-CT00-TRNID-LAST`, `CDEMO-CT00-PAGE-NUM`, `CDEMO-CT00-NEXT-PAGE-FLG` 갱신

---

## 의존성

- **COPY (카피북)**:

  | Copybook | 경로 | 역할 |
  |---|---|---|
  | `COCOM01Y` | `app/cpy/COCOM01Y.cpy` | 공통 COMMAREA (`CARDDEMO-COMMAREA`), CT00 전용 섹션 포함 |
  | `COTRN00` | `app/cpy-bms/COTRN00.CPY` | BMS 맵셋 심볼릭 맵 (`COTRN0AI` / `COTRN0AO`) |
  | `COTTL01Y` | `app/cpy/COTTL01Y.cpy` | 화면 공통 타이틀 문자열 (`CCDA-TITLE01`, `CCDA-TITLE02`) |
  | `CSDAT01Y` | `app/cpy/CSDAT01Y.cpy` | 날짜·시간 작업 변수 (`WS-DATE-TIME`, `WS-TIMESTAMP`) |
  | `CSMSG01Y` | `app/cpy/CSMSG01Y.cpy` | 공통 오류 메시지 리터럴 (`CCDA-MSG-INVALID-KEY` 등) |
  | `CVTRA05Y` | `app/cpy/CVTRA05Y.cpy` | TRANSACT 파일 레코드 레이아웃 (`TRAN-RECORD`, 350바이트) |
  | `DFHAID` | CICS 시스템 | AID(Attention Identifier) 상수 (`DFHENTER`, `DFHPF3`, `DFHPF7`, `DFHPF8`) |
  | `DFHBMSCA` | CICS 시스템 | BMS 속성 바이트 상수 |

- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `XCTL COTRN01C` — 사용자가 거래를 선택(`S`)했을 때 거래 상세 화면으로 제어 이전 (라인 193)
  - `XCTL COMEN01C` — PF3 메뉴 복귀 (라인 123~124)
  - `XCTL COSGN00C` — EIBCALEN=0 (세션 미초기화) 또는 `CDEMO-TO-PROGRAM` 미설정 시 로그인 화면으로 이전 (라인 108~109, 512~513)

- **데이터셋/파일/DB 테이블**:
  - `TRANSACT` (VSAM KSDS) — TRAN-ID(PIC X(16)) 기준 키 순서 브라우즈. CICS `STARTBR`/`READNEXT`/`READPREV`/`ENDBR` 사용

- **트랜잭션 ID 또는 EXEC PGM**:
  - CICS 트랜잭션 ID: **`CT00`** (`WS-TRANID`, 라인 37)
  - BMS 맵셋: `COTRN00` / 맵: `COTRN0A`

---

## 핵심 로직 흐름

아래 흐름은 PROCEDURE DIVISION 전체를 단계별로 추적한 것이다.

### 1. MAIN-PARA — 진입점 및 키 분기 (라인 95~141)

```
EIBCALEN = 0?
  ├─ Yes → COSGN00C로 XCTL (세션 없음, 로그인으로)
  └─ No  → COMMAREA 복원 (DFHCOMMAREA → CARDDEMO-COMMAREA)
              CDEMO-PGM-REENTER = 0? (첫 진입)
              ├─ Yes → CDEMO-PGM-REENTER = 1 설정
              │         PROCESS-ENTER-KEY
              │         SEND-TRNLST-SCREEN
              └─ No  → RECEIVE-TRNLST-SCREEN
                         EIBAID 분기:
                           ENTER  → PROCESS-ENTER-KEY
                           PF3    → COMEN01C XCTL
                           PF7    → PROCESS-PF7-KEY
                           PF8    → PROCESS-PF8-KEY
                           기타   → 오류 메시지 + SEND-TRNLST-SCREEN
              EXEC CICS RETURN TRANSID('CT00') COMMAREA(...)
```

**pseudo-conversational 패턴**: 프로그램은 화면을 전송한 뒤 즉시 `RETURN`으로 CICS에 제어를 돌려준다. 사용자가 다음 키를 누를 때 `CT00` 트랜잭션으로 다시 기동되며, COMMAREA를 통해 페이지 상태를 이어받는다.

### 2. PROCESS-ENTER-KEY (라인 146~229)

**선택 처리**: SEL0001I~SEL0010I 중 공백·LOW-VALUE가 아닌 첫 번째 필드를 찾아 `CDEMO-CT00-TRN-SEL-FLG`와 `CDEMO-CT00-TRN-SELECTED`에 기록한다(EVALUATE TRUE 구조, 라인 148~182).

```
SEL 값이 'S' 또는 's'?
  └─ XCTL COTRN01C (COMMAREA에 선택 거래 ID 포함)
그 외 SEL 값?
  └─ 오류 메시지 ("Invalid selection. Valid value is S")
```

**검색 키 처리**: `TRNIDINI`(입력 필드)가 비어 있으면 `TRAN-ID = LOW-VALUES`(처음부터 시작), 숫자 값이 있으면 해당 ID를 검색 시작 키로 사용. 숫자가 아니면 입력 오류 처리 후 화면 재전송.

이후 `CDEMO-CT00-PAGE-NUM = 0`으로 초기화하고 `PROCESS-PAGE-FORWARD` 호출.

### 3. PROCESS-PF7-KEY — 이전 페이지 (라인 234~252)

현재 페이지 첫 번째 레코드(`CDEMO-CT00-TRNID-FIRST`)를 `TRAN-ID`에 적재한 뒤 `PROCESS-PAGE-BACKWARD` 호출. `CDEMO-CT00-PAGE-NUM`이 1이면 "이미 첫 페이지"라는 메시지를 출력하고 멈춘다.

### 4. PROCESS-PF8-KEY — 다음 페이지 (라인 257~274)

현재 페이지 마지막 레코드(`CDEMO-CT00-TRNID-LAST`)를 `TRAN-ID`에 적재. `NEXT-PAGE-YES` 플래그가 설정된 경우에만 `PROCESS-PAGE-FORWARD` 호출. 이미 마지막 페이지라면 "already at the bottom" 메시지.

### 5. PROCESS-PAGE-FORWARD (라인 279~328)

```
STARTBR-TRANSACT-FILE (TRAN-ID 키부터 브라우즈 오픈)
  │
  ├─ EIBAID가 ENTER/PF7/PF3이 아닌 경우 → READNEXT 1회 스킵
  │   (초기 Enter 시 현재 키 레코드 자체를 건너뛰어 다음부터 표시)
  │
  ├─ INITIALIZE-TRAN-DATA × 10회 (화면 행 초기화)
  │
  ├─ WS-IDX=1 → UNTIL WS-IDX > 10 OR EOF:
  │     READNEXT-TRANSACT-FILE
  │     POPULATE-TRAN-DATA  (IDX번째 행에 데이터 채움)
  │     WS-IDX + 1
  │
  ├─ EOF 미도달? → READNEXT 1회 더 시도
  │     성공 → NEXT-PAGE-YES (다음 페이지 존재)
  │     실패 → NEXT-PAGE-NO
  │
  ├─ CDEMO-CT00-PAGE-NUM + 1
  └─ ENDBR-TRANSACT-FILE → SEND-TRNLST-SCREEN
```

### 6. PROCESS-PAGE-BACKWARD (라인 333~376)

READPREV를 이용해 역방향으로 10건을 수집한다. WS-IDX=10부터 시작하여 1씩 감소하며 `POPULATE-TRAN-DATA`를 호출하므로, 화면에 표시될 때는 키 순서(오름차순)가 유지된다.

```
STARTBR-TRANSACT-FILE
  ├─ EIBAID가 ENTER/PF8이 아닌 경우 → READPREV 1회 스킵
  ├─ INITIALIZE-TRAN-DATA × 10회
  ├─ WS-IDX=10 → UNTIL WS-IDX <= 0 OR EOF:
  │     READPREV-TRANSACT-FILE
  │     POPULATE-TRAN-DATA
  │     WS-IDX - 1
  ├─ READPREV 1회 더 → 성공이면 PAGE-NUM - 1, 실패면 PAGE-NUM = 1
  └─ ENDBR → SEND-TRNLST-SCREEN
```

> 주의: 역방향 브라우즈 후 POPULATE-TRAN-DATA는 WS-IDX가 10→1로 감소하므로 화면 행 10번에 가장 오래된(키 값이 작은) 레코드가, 행 1번에 최신 레코드가 들어간다. 즉, **역방향 페이지 이동 시 화면 행 순서가 역전된다**. 이는 일반적인 페이지네이션 UX와 다를 수 있으므로 Java 마이그레이션 시 유의가 필요하다.

### 7. POPULATE-TRAN-DATA (라인 381~445)

`TRAN-RECORD`에서 읽은 데이터를 BMS 맵 입력 구조(`COTRN0AI`)의 해당 행에 채운다.

```cobol
TRAN-AMT    → WS-TRAN-AMT (PIC +99999999.99 편집 형식으로 변환)
TRAN-ORIG-TS → WS-TIMESTAMP → WS-CURDATE-MM/DD/YY → WS-TRAN-DATE (MM/DD/YY 형식)
TRAN-ID     → TRNID{nn}I, WS-IDX=1이면 CDEMO-CT00-TRNID-FIRST, 10이면 CDEMO-CT00-TRNID-LAST
TRAN-DESC   → TDESC{nn}I
```

### 8. 파일 I/O 단락

| 단락 | CICS 명령 | 설명 |
|---|---|---|
| `STARTBR-TRANSACT-FILE` | `EXEC CICS STARTBR DATASET RIDFLD KEYLENGTH` | 키 위치에서 브라우즈 커서 설정. NOTFND 응답은 EOF처리 |
| `READNEXT-TRANSACT-FILE` | `EXEC CICS READNEXT` | 키 오름차순 순차 읽기. ENDFILE = EOF |
| `READPREV-TRANSACT-FILE` | `EXEC CICS READPREV` | 키 내림차순 순차 읽기. ENDFILE = EOF |
| `ENDBR-TRANSACT-FILE` | `EXEC CICS ENDBR` | 브라우즈 세션 종료 |

모든 파일 I/O 단락은 `DFHRESP(NORMAL)`, `DFHRESP(NOTFND/ENDFILE)`, `WHEN OTHER` 3분기로 오류를 처리한다 (라인 602~619, 636~653, 670~687).

### 9. 화면 I/O 단락

- `POPULATE-HEADER-INFO` (라인 567~586): `FUNCTION CURRENT-DATE`로 현재 날짜/시간을 가져와 헤더 필드에 채운다.
- `SEND-TRNLST-SCREEN` (라인 527~549): `WS-SEND-ERASE-FLG`가 Y이면 `ERASE` 옵션 포함(`EXEC CICS SEND MAP ... ERASE`), N이면 ERASE 생략(페이지 메시지 전용 재전송).
- `RECEIVE-TRNLST-SCREEN` (라인 554~562): `EXEC CICS RECEIVE MAP INTO(COTRN0AI)`.

---

## Java/현대화 노트

### 1. pseudo-conversational → Spring MVC / REST 매핑

| COBOL 메커니즘 | Java/현대 상응 |
|---|---|
| `EXEC CICS RETURN TRANSID COMMAREA` | HTTP 응답 반환 후 세션 종료; 다음 요청까지 서버 상태 없음 |
| `CARDDEMO-COMMAREA` | `HttpSession` 또는 JWT 클레임에 페이지 상태 보관 |
| `EIBCALEN = 0` 체크 | 세션 미인증 → 로그인 리다이렉트 |
| `CDEMO-PGM-REENTER` 플래그 | 첫 진입 여부 → `session.getAttribute("initialized") == null` |

### 2. 페이지네이션 패턴

COBOL은 VSAM 브라우즈 커서 위치(첫/마지막 키)를 COMMAREA에 저장하여 다음 요청 시 복원한다. 이는 **keyset(cursor) pagination** 방식이다.

```java
// Java 상응 — Spring Data + Keyset Pagination
public Page<Transaction> findNext(String afterId, int pageSize) {
    return transactionRepository
        .findByIdGreaterThanOrderByIdAsc(afterId, PageRequest.of(0, pageSize));
}

public Page<Transaction> findPrev(String beforeId, int pageSize) {
    // 역방향: ID < beforeId, 내림차순으로 10건 조회 후 List 역순 정렬
    List<Transaction> rows = transactionRepository
        .findByIdLessThanOrderByIdDesc(beforeId, PageRequest.of(0, pageSize))
        .getContent();
    Collections.reverse(rows); // COBOL과 동일하게 화면 표시 순서 정방향으로
    return new PageImpl<>(rows);
}
```

> 주의: 현재 COBOL의 역방향 브라우즈는 화면 행이 역전된다(WS-IDX 10→1). Java 마이그레이션 시 Collections.reverse()로 보정하거나 UX를 재설계해야 한다.

### 3. TRAN-AMT 데이터 타입

```
TRAN-AMT  PIC S9(09)V99   -- 부호 있는 9자리 정수 + 2자리 소수 (DISPLAY 형식)
```

DISPLAY 형식은 EBCDIC 존(zone) 인코딩이다. Java 변환 시:

```java
// COBOL S9(09)V99 DISPLAY → Java
// 정수 표현: ±999999999.99, V는 실제 소수점 없이 위치만 의미
BigDecimal amount = new BigDecimal(tranAmtString)
    .movePointLeft(2);   // V99 → 소수점 2자리 왼쪽 이동
// float/double 사용 금지 — 금액 정밀도 손실 위험
```

### 4. TRAN-ORIG-TS 날짜 파싱

```
TRAN-ORIG-TS  PIC X(26)  -- "YYYY-MM-DD HH:MM:SS.mmmmmm" 형식(추측)
```

COBOL은 이를 WS-TIMESTAMP 구조(CSDAT01Y)로 오버레이하여 연·월·일을 추출한다(라인 384~388). Java 상응:

```java
LocalDateTime ts = LocalDateTime.parse(
    tranOrigTs.trim(),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
);
String displayDate = ts.format(DateTimeFormatter.ofPattern("MM/dd/yy"));
```

### 5. BMS 맵 → DTO 매핑

BMS 맵 `COTRN0AI`는 행 10개를 완전히 정적으로 펼쳐 놓은 구조다(SEL0001I~SEL0010I, TRNID01I~TRNID10I 등). Java에서는 리스트 기반 DTO로 교체한다.

```java
// COBOL의 10행 정적 배열 → Java List
public record TransactionRow(
    String id,        // TRNID{nn}I  PIC X(16)
    String date,      // TDATE{nn}I  PIC X(8)
    String desc,      // TDESC{nn}I  PIC X(26)
    String amount     // TAMT{nn}I   PIC X(12) (편집 후 문자열)
) {}

List<TransactionRow> rows = new ArrayList<>(); // 최대 10개
```

### 6. STARTBR GTEQ 주석 처리

라인 597에 `* GTEQ`가 주석 처리되어 있다. GTEQ 없이 STARTBR를 호출하면 정확히 `TRAN-ID`와 일치하는 레코드부터 브라우즈를 시작한다(동등 일치). GTEQ가 있으면 `>=` 검색이 된다. 현재 코드는 GTEQ를 사용하지 않으므로, 정확한 ID가 없으면 NOTFND가 반환된다. Java의 `>=` 쿼리(`findByIdGreaterThanOrEqualTo`)에 해당하려면 이 차이를 인지해야 한다.

### 7. XCTL vs CALL 차이

`EXEC CICS XCTL`은 호출 후 제어가 돌아오지 않는 단방향 이전이다. Java의 `response.sendRedirect()`에 가깝다. `EXEC CICS LINK`(사용 없음)는 메서드 호출에 해당한다.

### 8. OCCURS 미사용 → 정적 반복 코드

COBOL이 `OCCURS 10` 배열 대신 행마다 별도 필드를 선언(SEL0001I~SEL0010I 등)한 것은 BMS 맵 생성기의 제약 때문이다. Java 마이그레이션 시 이를 배열/리스트로 통합해 INITIALIZE-TRAN-DATA와 POPULATE-TRAN-DATA의 반복 코드를 루프로 단순화할 수 있다.

---

*소스 기준 버전: CardDemo_v1.0-15-g27d6c6f-68 (2022-07-19)*
