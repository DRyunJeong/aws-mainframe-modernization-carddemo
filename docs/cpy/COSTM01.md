# COSTM01 — 명세서용 거래 레코드 레이아웃

- **유형**: Copybook
- **한 줄 요약**: 카드 명세서 생성 시 사용하는 거래 레코드의 필드 레이아웃을 정의한다. CBSTM03A(명세서 드라이버)가 CBSTM03B로부터 돌려받은 원시 거래 데이터를 해석할 때 이 copybook으로 MOVE하여 사용한다.

## 기능 설명

COSTM01.CPY는 CardDemo 명세서 생성 배치(CREASTMT 잡)에서 거래 레코드 한 건을 표현하는 01 레벨 그룹 `TRNX-RECORD`를 정의한다.

헤더 주석(행 3)에 "Transaction **altered** Layout for use in reporting"이라고 명시되어 있다. 즉, 이 copybook은 온라인 거래 파일(TRANSACT KSDS)의 원본 레이아웃 copybook인 `CVTRA05Y`(`TRAN-RECORD`)를 그대로 쓰지 않고, 명세서 리포팅 목적에 맞게 필드명을 재작명하고 구조를 조정한 **리포팅 전용 복사본**이다.

CBSTM03A는 CBSTM03B(범용 I/O 서브루틴)가 돌려준 1,000바이트 범용 버퍼(FLDT)를 `TRNX-RECORD`로 MOVE하여 각 필드에 접근한다. 레코드 총 길이는 고정 350바이트이다.

레코드는 두 개의 02레벨 그룹으로 구성된다.
- `TRNX-KEY`: 거래 식별 키 2필드 (카드번호 16 + 거래ID 16 = 32바이트)
- `TRNX-REST`: 거래의 나머지 속성 전체 (318바이트 + FILLER 20바이트)

Java 관점에서는 이 01 레벨 전체가 하나의 불변 DTO(Data Transfer Object) 또는 레코드 클래스에 대응한다.

## 필드 레이아웃

| 필드명 | 레벨 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|---|
| `TRNX-RECORD` | 01 | GROUP | 350 | 거래 레코드 루트 그룹 |
| `TRNX-KEY` | 05 | GROUP | 32 | 거래 식별 키 (파일의 KSDS 키) |
| `TRNX-CARD-NUM` | 10 | PIC X(16) | 16 | 카드번호. VSAM 복합 키의 선두 요소 |
| `TRNX-ID` | 10 | PIC X(16) | 16 | 거래 고유 ID. 복합 키의 후행 요소 |
| `TRNX-REST` | 05 | GROUP | 318 | 거래 속성 그룹 |
| `TRNX-TYPE-CD` | 10 | PIC X(02) | 2 | 거래 유형 코드. TRANTYPE 파일(CVTRA03Y)의 유형명과 대응 |
| `TRNX-CAT-CD` | 10 | PIC 9(04) | 4 | 거래 카테고리 코드(DISPLAY 십진수). TRANCATG 파일(CVTRA04Y)의 카테고리명과 대응 |
| `TRNX-SOURCE` | 10 | PIC X(10) | 10 | 거래 발생 채널/출처 (예: 온라인, POS 등) |
| `TRNX-DESC` | 10 | PIC X(100) | 100 | 거래 설명(자유 텍스트) |
| `TRNX-AMT` | 10 | PIC S9(09)V99 | 11 | 거래 금액. 부호 포함, 소수점 2자리 묵시적(V). DISPLAY 포맷(주석 없으므로 기본값 적용) |
| `TRNX-MERCHANT-ID` | 10 | PIC 9(09) | 9 | 가맹점 ID (DISPLAY 십진수 9자리) |
| `TRNX-MERCHANT-NAME` | 10 | PIC X(50) | 50 | 가맹점 이름 |
| `TRNX-MERCHANT-CITY` | 10 | PIC X(50) | 50 | 가맹점 소재 도시 |
| `TRNX-MERCHANT-ZIP` | 10 | PIC X(10) | 10 | 가맹점 우편번호 |
| `TRNX-ORIG-TS` | 10 | PIC X(26) | 26 | 거래 원시 발생 타임스탬프 (ISO 8601 형식 추정: `YYYY-MM-DD HH:MM:SS.ssssss`) |
| `TRNX-PROC-TS` | 10 | PIC X(26) | 26 | 거래 처리 완료 타임스탬프 (동일 형식) |
| `FILLER` | 10 | PIC X(20) | 20 | 예약 패딩. 현재 미사용 |

**레코드 길이 검증**: 32 (KEY) + 2 + 4 + 10 + 100 + 11 + 9 + 50 + 50 + 10 + 26 + 26 + 20 = **350바이트** (TRANSACT KSDS의 LRECL=350과 일치).

**`TRNX-AMT` 저장 형식 주의**: `PIC S9(09)V99`에 `COMP-3` 또는 `COMP` 지정자가 없으므로 DISPLAY(존재 십진수, Zoned Decimal) 포맷이다. DISPLAY S9(09)V99는 11바이트를 차지하며, 마지막 바이트의 상위 니블에 부호(C=양수, D=음수)가 포함된다. 원본 `CVTRA05Y`의 동일 위치 필드도 DISPLAY로 정의되어 있으므로 정합성이 있다(추측).

**`TRNX-CAT-CD` 주의**: `PIC 9(04)` 즉 DISPLAY 십진수 4자리. 앞에 0을 채운 `"0030"` 같은 형식으로 저장된다. Java의 `int`로 변환할 때 `Integer.parseInt(String.valueOf(field).trim())`이 필요하다.

## 의존성

- **COPY (중첩 카피북)**: 없음 (COSTM01.CPY 자체에 다른 COPY 문 없음)
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**: TRNXFILE (VSAM KSDS, LRECL=350). CBSTM03A에서 DD명 `TRNXFILE`로 참조. 물리 데이터셋은 CREASTMT JCL에서 할당됨. CBSTM03B가 이 파일을 열고 닫으며, 데이터를 1,000바이트 범용 버퍼(FLDT)에 담아 CBSTM03A로 반환하면 CBSTM03A가 `TRNX-RECORD`로 MOVE함.
- **트랜잭션 ID 또는 EXEC PGM**: 없음

## Java / 현대화 노트

### 1. Java 레코드 클래스 매핑

COSTM01.CPY 전체는 다음 Java 레코드로 직접 변환된다.

```java
public record TrnxRecord(
    String  cardNum,          // TRNX-CARD-NUM  PIC X(16)
    String  id,               // TRNX-ID        PIC X(16)
    String  typeCd,           // TRNX-TYPE-CD   PIC X(02)
    int     catCd,            // TRNX-CAT-CD    PIC 9(04) → int
    String  source,           // TRNX-SOURCE    PIC X(10)
    String  desc,             // TRNX-DESC      PIC X(100)
    BigDecimal amt,           // TRNX-AMT       PIC S9(09)V99 → BigDecimal(scale=2)
    int     merchantId,       // TRNX-MERCHANT-ID PIC 9(09) → int
    String  merchantName,     // TRNX-MERCHANT-NAME PIC X(50)
    String  merchantCity,     // TRNX-MERCHANT-CITY PIC X(50)
    String  merchantZip,      // TRNX-MERCHANT-ZIP PIC X(10)
    Instant origTs,           // TRNX-ORIG-TS   PIC X(26) → Instant
    Instant procTs            // TRNX-PROC-TS   PIC X(26) → Instant
    // FILLER X(20) — 폐기
) {}
```

### 2. `TRNX-AMT` 변환 시 BigDecimal 필수

`PIC S9(09)V99`는 묵시적 소수점(V)이 있으므로 실제 저장값에는 소수점 문자가 없다. DISPLAY 포맷으로 EBCDIC에서 ASCII로 변환한 뒤 `new BigDecimal(rawString).movePointLeft(2)` 또는 파싱 시 scale 2를 명시해야 한다. `double`/`float`로 변환하면 이진 부동소수점 오차가 발생하므로 금액 필드에는 반드시 `BigDecimal`을 사용해야 한다.

### 3. 타임스탬프 파싱

`TRNX-ORIG-TS`와 `TRNX-PROC-TS`는 26자 문자열이다. 실제 데이터 형식이 `YYYY-MM-DD HH:MM:SS.ssssss`라면(추측 — 실제 데이터 샘플 확인 필요) 다음 코드를 사용한다.

```java
DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
Instant origTs = LocalDateTime.parse(raw.trim(), FMT)
                               .toInstant(ZoneOffset.UTC); // 시간대 정책 별도 확인 필요
```

메인프레임은 로컬 타임존(보통 EST/CST)으로 저장하는 경우가 많으므로 시간대 처리 정책을 시스템 설계 문서에서 확인해야 한다.

### 4. 고정 길이 문자열 처리

메인프레임 DISPLAY 필드는 우측에 공백(0x40 EBCDIC, 0x20 ASCII)으로 패딩된다. Java로 읽을 때 반드시 `.trim()` 또는 `StringUtils.trimRight()`를 적용하지 않으면 문자열 비교 버그가 발생한다. 예: `TRNX-MERCHANT-NAME`에 저장된 `"AMAZON          "` 을 `"AMAZON"` 으로 정규화해야 한다.

### 5. TRNX-KEY와 VSAM 키 구조

`TRNX-KEY`(32바이트)는 VSAM KSDS의 복합 키이다. Java에서 이 레코드를 DB로 마이그레이션할 때 `(card_num, trnx_id)`가 복합 기본키가 된다. `TRNX-CARD-NUM`은 `CVACT02Y`의 `CARD-NUM`과 동일 도메인이므로 외래키 제약도 설정 가능하다.

### 6. COSTM01 vs CVTRA05Y 차이점

에이전트 메모리(copybook-record-layouts)에 따르면 온라인 거래 파일의 원본 copybook은 `CVTRA05Y`(`TRAN-RECORD`)이다. COSTM01은 리포팅용 "altered" 버전으로, 필드명의 접두사가 `TRAN-`에서 `TRNX-`로 바뀌었다. 바이트 레이아웃은 동일할 가능성이 높으나, 마이그레이션 시 두 copybook의 필드 순서와 PIC 길이를 바이트 단위로 교차 검증해야 한다.

### 7. FILLER 20바이트

행 36의 `FILLER PIC X(20)`은 현재 미사용 예약 영역이다. 파일 레코드를 Java 객체로 역직렬화할 때 이 20바이트는 건너뛰어야 하며, 향후 필드 추가를 위한 확장 공간으로 주석을 달아두는 것이 좋다.
