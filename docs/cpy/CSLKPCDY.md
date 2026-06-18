# CSLKPCDY — 북미 전화 지역번호 / 미국 주 코드 / 주-우편번호 룩업 테이블

- **유형**: Copybook (검증 전용 룩업 테이블)
- **한 줄 요약**: 북미 전화 지역번호(490개), 미국 주 코드(56개), 주+우편번호 앞 2자리 조합(240개)을 88-level 조건명 집합으로 정의하여, 고객 입력값의 형식 유효성을 `IF VALID-xxx` 한 줄로 검증할 수 있게 한다.

---

## 기능 설명

이 copybook은 데이터를 저장하거나 전달하지 않는다. 오로지 **인라인 룩업 테이블(lookup table)** 역할만 한다. COBOL에서 88-level 조건명은 부모 데이터 항목의 값을 미리 열거된 집합과 비교하는 불리언 술어(predicate)다. 즉, 이 copybook을 COPY하면 세 개의 "검증 도구 변수"가 WORKING-STORAGE에 추가되고, 프로그램은 이 변수에 검증 대상 값을 MOVE한 뒤 IF 조건명으로 유효 여부를 즉시 판별한다.

```cobol
* 예시 — COACTUPC.cbl 2296~2298행
MOVE FUNCTION TRIM (WS-EDIT-US-PHONE-NUMA)
  TO WS-US-PHONE-AREA-CODE-TO-EDIT
IF VALID-GENERAL-PURP-CODE ...
```

Java로 비유하면 `Set<String>` 세 개를 static final 상수로 선언하고 `set.contains(value)`로 검증하는 구조와 동일하다.

세 개의 룩업 테이블은 다음과 같이 계층을 이룬다.

```
VALID-PHONE-AREA-CODE (490개, 전체 유효 지역번호)
├── VALID-GENERAL-PURP-CODE (410개, 일반 지역번호)
└── VALID-EASY-RECOG-AREA-CODE (80개, 패턴 인식 번호)
     예: 200, 211, 222, ... 800, 888, 911, 999 등 N11·N00·패턴 코드
```

두 하위 집합은 서로 겹치지 않으며(`교집합 = 0`), 합집합이 정확히 `VALID-PHONE-AREA-CODE`와 일치한다(소스 30~520행, 521~930행, 931~1010행으로 검증됨).

---

## 필드 레이아웃

### 01 WS-US-PHONE-AREA-CODE-TO-EDIT (소스 24행)

| 필드명 | PIC / USAGE | 크기 | 의미 |
|---|---|---|---|
| `WS-US-PHONE-AREA-CODE-TO-EDIT` | `PIC XXX` | 3바이트 DISPLAY | 검증 대상 전화 지역번호를 담는 임시 변수. MOVE 후 아래 88-level로 판별. |

**88-level 조건명 (부모: WS-US-PHONE-AREA-CODE-TO-EDIT)**

| 조건명 | 소스 행 | 값 개수 | 의미 |
|---|---|---|---|
| `VALID-PHONE-AREA-CODE` | 30~520 | **490개** | 북미 번호 계획(NANP) 기준 전체 유효 지역번호 (일반 + 쉽게 인식되는 코드 모두 포함). NANPA(https://nationalnanpa.com) CSV 기준. |
| `VALID-GENERAL-PURP-CODE` | 521~930 | **410개** | 일반 목적의 유효 지역번호. 201~989 범위 중 실제 할당된 번호. COACTUPC.cbl에서 실제 전화번호 검증에 사용되는 조건명. |
| `VALID-EASY-RECOG-AREA-CODE` | 931~1010 | **80개** | 쉽게 인식되는 패턴 코드(200, 211, 222, 233, ..., 800, 811, 888, 911, 999 등). N11(정보 서비스), N00(수신자 부담 등), AAA(모두 동일한 세 자리) 패턴이 대부분. |

> **구조 주의**: `VALID-PHONE-AREA-CODE`는 `VALID-GENERAL-PURP-CODE`와 `VALID-EASY-RECOG-AREA-CODE`의 합집합을 VALUES 절에 직접 나열한 형태다. COBOL 88-level은 부분집합 관계를 언어 차원에서 표현하지 않기 때문에, 두 하위 조건명의 값 목록을 그대로 반복하여 하나의 조건명으로 합쳐 놓은 것이다.

---

### 01 US-STATE-CODE-TO-EDIT (소스 1012행)

| 필드명 | PIC / USAGE | 크기 | 의미 |
|---|---|---|---|
| `US-STATE-CODE-TO-EDIT` | `PIC X(2)` | 2바이트 DISPLAY | 검증 대상 미국 주 코드를 담는 임시 변수. |

**88-level 조건명**

| 조건명 | 소스 행 | 값 개수 | 내용 |
|---|---|---|---|
| `VALID-US-STATE-CODE` | 1013~1069 | **56개** | 미국 50개 주 코드(AL·AK·AZ…WY) + DC + 영토 5개(AS·GU·MP·PR·VI). |

미국 주 코드 전체 목록 (알파벳 순):

```
AL AK AZ AR CA CO CT DE FL GA HI ID IL IN IA KS KY LA ME MD
MA MI MN MS MO MT NE NV NH NJ NM NY NC ND OH OK OR PA RI SC
SD TN TX UT VT VA WA WV WI WY DC AS GU MP PR VI
```

---

### 01 US-STATE-ZIPCODE-TO-EDIT (소스 1071행)

이 구조는 단순 PIC 필드가 아닌 **그룹 항목(group item)** 이다.

| 필드명 | 레벨 | PIC / USAGE | 크기 | 의미 |
|---|---|---|---|---|
| `US-STATE-ZIPCODE-TO-EDIT` | 01 | 그룹 | 5바이트 | 주 코드 + 우편번호 검증용 복합 구조 |
| `US-STATE-AND-FIRST-ZIP2` | 02 | `PIC X(4)` | 4바이트 | 주 코드 2자리 + 우편번호 앞 2자리를 연결한 키. 아래 88-level의 부모. |
| `LAST-3-OF-ZIP` | 02 | `PIC X(3)` | 3바이트 | 우편번호 뒤 3자리 (검증에는 사용되지 않고 단순 저장용으로 정의됨). |

**88-level 조건명 (부모: US-STATE-AND-FIRST-ZIP2)**

| 조건명 | 소스 행 | 값 개수 | 의미 |
|---|---|---|---|
| `VALID-US-STATE-ZIP-CD2-COMBO` | 1073~1313 | **240개** | 주 코드 + 우편번호 앞 2자리의 유효한 조합. 예: `'CA90'`, `'NY10'`, `'TX75'` 등. USPS 공식 데이터 기반(소스 2535행 주석: "A crude zip code edit based on data from USPS web site"). |

주-우편번호 조합 샘플:

```
AA34  AE90~98  AK99  AL35~36  AP96  AR71~72  AS96
AZ85~86  CA90~96  CO80~81  CT60~69  DC20/56/88  DE19
FL32~34  FM96  GA30~31/39  GU96  HI96  IA50~52
ID83  IL60~62  IN46~47  KS66~67  KY40~42  LA70~71
... (중략) ...
TX73/75~79/88  UT84  VA20/22~24  VI80/82~85  VT50~59
WA98~99  WI53~54  WV24~26  WY82~83
```

> **설계 의도**: 5자리 우편번호 전체를 검증하면 수만 개의 값이 필요하지만, 앞 2자리만 주 코드와 조합하면 240개로 "조잡하지만 실용적인(crude)" 1차 필터를 구현할 수 있다. 소스 주석도 "crude zip code edit"이라 명시한다.

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — 이 copybook 자체가 다른 copybook을 COPY하지 않음.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**: 없음 — 모든 룩업 데이터가 소스 내 88-level VALUES 절에 하드코딩되어 있음.
- **트랜잭션 ID 또는 EXEC PGM**: 없음

**이 copybook을 COPY하는 프로그램:**

| 프로그램 | 소스 행 | 사용 조건명 | 사용 문맥 |
|---|---|---|---|
| `COACTUPC.cbl` | 602 | `VALID-GENERAL-PURP-CODE` (2298행) | 고객 계정 수정 화면 — 전화번호 지역번호 검증 |
| `COACTUPC.cbl` | 602 | `VALID-US-STATE-CODE` (2495행) | 고객 주소 주 코드 검증 |
| `COACTUPC.cbl` | 602 | `VALID-US-STATE-ZIP-CD2-COMBO` (2542행) | 주 코드 + 우편번호 앞 2자리 조합 검증 |

> **주의**: `VALID-PHONE-AREA-CODE`와 `VALID-EASY-RECOG-AREA-CODE`는 이 copybook에 정의되어 있지만, 현재 `COACTUPC.cbl`에서는 참조되지 않는다. `VALID-GENERAL-PURP-CODE`(일반 지역번호만)만 사용한다 — 즉, 쉽게 인식되는 패턴 코드(800, 888, 900 등)는 전화번호 입력에서 **거부된다**.

---

## Java/현대화 노트

### 1. 88-level → `Set<String>` 또는 `enum`

COBOL 88-level은 Java에 직접 대응하는 구조가 없다. 현대화 시 가장 자연스러운 매핑은 `Set<String>` 상수다.

```java
// COBOL: 88 VALID-GENERAL-PURP-CODE VALUES '201', '202', ...
public final class PhoneAreaCodes {
    public static final Set<String> GENERAL_PURPOSE = Set.of(
        "201", "202", "203", /* ... */ "989"
    );
    public static final Set<String> EASY_RECOGNIZABLE = Set.of(
        "200", "211", "222", /* ... */ "999"
    );
    public static final Set<String> ALL_VALID =
        Stream.concat(GENERAL_PURPOSE.stream(), EASY_RECOGNIZABLE.stream())
              .collect(Collectors.toUnmodifiableSet());

    public static boolean isValidGeneralPurpose(String areaCode) {
        return GENERAL_PURPOSE.contains(areaCode);
    }
}
```

```java
// COBOL: 88 VALID-US-STATE-CODE VALUES 'AL', 'AK', ...
public enum UsState {
    AL, AK, AZ, AR, CA, CO, CT, DE, FL, GA, HI, ID, IL, IN, IA,
    KS, KY, LA, ME, MD, MA, MI, MN, MS, MO, MT, NE, NV, NH, NJ,
    NM, NY, NC, ND, OH, OK, OR, PA, RI, SC, SD, TN, TX, UT, VT,
    VA, WA, WV, WI, WY, DC, AS, GU, MP, PR, VI;

    private static final Set<String> CODES =
        Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

    public static boolean isValid(String code) {
        return CODES.contains(code);
    }
}
```

### 2. 주-우편번호 조합 검증 로직 재현

```java
// COBOL: STRING state-cd zip(1:2) INTO US-STATE-AND-FIRST-ZIP2
//        IF VALID-US-STATE-ZIP-CD2-COMBO
public static final Set<String> VALID_STATE_ZIP2_COMBOS = Set.of(
    "AA34", "AE90", "AK99", "AL35", "AL36", /* ... */
    "WA98", "WA99", "WI53", "WI54", "WV24", "WY82", "WY83"
);

public static boolean isValidStateZip(String stateCode, String zipCode) {
    if (stateCode == null || zipCode == null || zipCode.length() < 2) return false;
    String key = stateCode + zipCode.substring(0, 2);
    return VALID_STATE_ZIP2_COMBOS.contains(key);
}
```

### 3. 현대화 시 주의사항

| 항목 | 내용 |
|---|---|
| **데이터 최신성** | NANPA 지역번호 목록은 지속적으로 변경된다. 490개 값이 COBOL 소스에 하드코딩되어 있어 신규 지역번호 추가 시 소스 수정 + 재컴파일이 필요하다. Java 마이그레이션 시 외부 설정 파일(DB, properties, 혹은 NANPA API) 기반으로 전환을 권장한다. |
| **우편번호 검증의 조잡함** | 소스 주석도 "crude"라 명시한다. 앞 2자리만 확인하므로 동일 2자리 내 잘못된 우편번호를 통과시킬 수 있다. 마이그레이션 시 USPS Address Validation API나 전체 우편번호 DB로 대체를 검토하라. |
| **VALID-EASY-RECOG-AREA-CODE 미사용** | 800, 888, 900, 911, 999 같은 패턴 코드는 `VALID-EASY-RECOG-AREA-CODE`로 분리되어 있으며, 현재 COACTUPC는 이를 **유효하지 않은 번호로 처리**한다. 비즈니스 규칙 문서화 시 명시할 것. |
| **영토 포함** | `VALID-US-STATE-CODE`에는 50개 주 외에 DC, AS, GU, MP, PR, VI(미국령 버진아일랜드) 총 6개가 포함된다. Java `enum`이나 유효성 검사기 구현 시 누락하지 말 것. |
| **PIC XXX는 DISPLAY** | `WS-US-PHONE-AREA-CODE-TO-EDIT PIC XXX`는 EBCDIC 3바이트 문자열이다. ASCII 환경에서는 그대로 `String`으로 처리 가능하나, 메인프레임 파일에서 직접 읽는 경우 EBCDIC→UTF-8 변환이 필요하다. |
| **그룹 항목 주의** | `US-STATE-ZIPCODE-TO-EDIT`는 5바이트 그룹 항목이다. COBOL에서 `STRING ... INTO US-STATE-AND-FIRST-ZIP2`(4바이트)는 부모 그룹의 앞 4바이트에 쓰고, `LAST-3-OF-ZIP`(3바이트)은 뒤 3바이트에 별도로 사용된다. Java 변환 시 이 두 필드를 별개의 변수로 분리해서 다루면 된다. |

### 4. 전체 아키텍처 위치

```
COACTUPC (고객 계정 수정 온라인 CICS 트랜잭션)
  └─ COPY CSLKPCDY  → 입력 유효성 검증 룩업 테이블 주입
       ├─ 전화 지역번호 검증  (1270-EDIT-US-PHONE 단락)
       ├─ 주 코드 검증       (1270-EDIT-US-STATE-CD 단락)
       └─ 주-우편번호 검증   (1280-EDIT-US-STATE-ZIP-CD 단락)
```

Java Spring 환경이라면 이 세 검증 로직을 Bean Validation(`@Constraint`) 또는 `Validator` 인터페이스로 분리하여 서비스 레이어와 독립적으로 테스트하는 구조를 권장한다.

---

*소스 버전: CardDemo_v1.0-15-g27d6c6f-68, 2022-07-19 (소스 1316행)*
