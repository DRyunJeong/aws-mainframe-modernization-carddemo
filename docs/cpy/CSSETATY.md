# CSSETATY — BMS 화면 속성 설정 인라인 매크로(Pseudo-Code Template)

- **유형**: Copybook (COPY 문으로 인라인 삽입되는 파라미터화 코드 조각)
- **한 줄 요약**: 화면 필드 하나를 "오류 또는 공백" 상태로 검사하여 BMS 속성 색상을 RED로, 공백이면 `*` 마스킹 문자까지 추가 설정하는 조건-SET 로직을 재사용 가능한 텍스트 조각으로 제공한다.

---

## 기능 설명

CSSETATY.cpy는 CardDemo 온라인(CICS/BMS) 프로그램들이 입력 검증 결과를 화면에 반영할 때 반복적으로 사용하는 **인라인 코드 조각(inline snippet)** 이다. 파일 자체에는 완전한 COBOL 문장이 담겨 있지만, 코드 내 괄호 표기(`(TESTVAR1)`, `(SCRNVAR2)`, `(MAPNAME3)`)는 **컴파일 타임 텍스트 치환용 자리표시자(placeholder)** 임을 나타낸다.

이 패턴은 IBM Enterprise COBOL의 표준 COPY 문만으로는 파라미터 치환이 불가능하므로, 실제 사용 시에는 **COPY ... REPLACING** 구문으로 자리표시자를 실제 필드명/맵명으로 교체하여 삽입하는 방식으로 동작한다(추측: REPLACING 없이 직접 COPY한다면 `(TESTVAR1)` 등이 그대로 남아 컴파일 오류가 발생한다).

동작 흐름(의사 코드):

```
IF (해당 필드가 오류 상태 OR 공백 상태)
   AND 화면 재진입 중(CDEMO-PGM-REENTER = TRUE)
THEN
    해당 화면 필드의 색상 속성(C 서픽스) = DFHRED  ← 빨간색 강조
    IF 필드가 공백 상태이면
        해당 화면 필드의 출력값(O 서픽스) = '*'    ← 공백 마스킹
    END-IF
END-IF
```

핵심 포인트:
- `CDEMO-PGM-REENTER`: `COCOM01Y`에 정의된 commarea 플래그. TRUE이면 사용자가 화면을 이미 한 번 거쳤다는 의미이다. 첫 진입 시에는 속성을 변경하지 않아 불필요한 오류 강조를 방지한다.
- `FLG-(TESTVAR1)-NOT-OK`: BMS symbolic map의 플래그 필드(F 서픽스) 또는 별도 플래그 변수에 대한 88-level 조건명 패턴으로 추정된다.
- `FLG-(TESTVAR1)-BLANK`: 동일 플래그 변수의 "공백(미입력)" 상태를 나타내는 88-level 조건명.
- `(SCRNVAR2)C OF (MAPNAME3)O`: BMS symbolic map의 색상 속성 바이트. C 서픽스 = Color 속성, O = 출력 구조체.
- `(SCRNVAR2)O OF (MAPNAME3)O`: 해당 필드의 실제 출력값(화면에 표시되는 문자열). O 서픽스 = Output value.
- `DFHRED`: CICS 표준 BMS 색상 상수(값 = X'02'). 3270 터미널에서 빨간색을 지정한다.

---

## 필드 레이아웃

이 copybook은 독립적인 `01` 레벨 데이터 구조를 정의하지 않는다. 대신 아래 자리표시자를 포함한 조건·이동 문장을 삽입한다.

| 자리표시자 | 교체 대상 예시 | 역할 |
|---|---|---|
| `(TESTVAR1)` | `ACCT-ID`, `TRAN-AMT` 등 입력 필드명 | 검증할 필드를 지칭하는 FLG-... 조건명의 일부 |
| `(SCRNVAR2)` | `ACCTIDD`, `TRANAMTD` 등 맵 필드명 | BMS symbolic map 내 색상/출력 속성 서픽스를 가진 필드명 |
| `(MAPNAME3)` | `COACTUPO`, `COTRN1AO` 등 맵 그룹명 | BMS symbolic map의 출력 구조체(`...O`) 이름 |

BMS 속성 서픽스 참고(메모리 copybook-record-layouts 컨벤션):

| 서픽스 | 데이터형 | 의미 |
|---|---|---|
| `L` | `PIC S9(4) COMP` | 필드 길이 / 커서 위치 |
| `F` | `PIC X` | 속성 플래그(MDT·보호·밝기 등) |
| `I` / `O` | `PIC X(n)` | 입력값 / 출력값 |
| `C` | `PIC X` | 색상 속성 바이트 (DFHRED, DFHBLUE 등) |
| `P` | `PIC X` | 강조(Highlight) 속성 |
| `H` | `PIC X` | 숨김(Hidden) 속성 |
| `V` | `PIC X` | 검증 속성 |

88-level 조건명 패턴(추측 — 실제 정의는 각 프로그램의 WORKING-STORAGE 또는 다른 copybook에 있음):

| 조건명 패턴 | 의미 |
|---|---|
| `FLG-(필드명)-NOT-OK` | 해당 필드 검증 실패 |
| `FLG-(필드명)-BLANK` | 해당 필드가 공백(미입력) |

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — 이 copybook 자체가 다른 copybook을 COPY하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**: 없음 — 화면 속성 조작 전용이며 파일 I/O가 없다.
- **트랜잭션 ID 또는 EXEC PGM**: 없음

이 copybook을 COPY하는 프로그램은 다음 외부 항목을 제공해야 한다:

| 항목 | 출처 |
|---|---|
| `CDEMO-PGM-REENTER` (88-level 조건) | `COCOM01Y` copybook의 `CARDDEMO-COMMAREA` |
| `DFHRED` 및 기타 BMS 색상 상수 | CICS 시스템 표준 (`DFHBMSCA` copybook 또는 CICS 내장 리터럴) |
| `FLG-(TESTVAR1)-NOT-OK`, `FLG-(TESTVAR1)-BLANK` | 사용 프로그램의 WORKING-STORAGE 내 88-level 정의 |
| `(SCRNVAR2)C/O OF (MAPNAME3)O` | BMS symbolic map copybook (`app/cpy-bms/*.CPY`) |

---

## Java/현대화 노트

### 1. 이 copybook이 하는 일을 Java로 표현하면

Java에서 가장 가까운 패턴은 **유틸리티 메서드** 또는 **유효성 검증 후 UI 상태 업데이트 로직** 이다.

```java
// CSSETATY 로직의 Java 등가 코드
private void applyErrorAttribute(FieldViewModel field, boolean isReEnter) {
    if ((field.isNotOk() || field.isBlank()) && isReEnter) {
        field.setColor(Color.RED);   // DFHRED → CSS/ANSI 빨간색
        if (field.isBlank()) {
            field.setDisplayValue("*");  // 공백 마스킹
        }
    }
}
```

실제 CardDemo를 Spring Boot + Thymeleaf로 현대화한다면:

```java
// Thymeleaf에서의 동등한 표현 (서버 사이드 렌더링)
// th:class="${field.hasError ? 'field-error' : ''}"
// th:value="${field.isBlank ? '*' : field.value}"
```

### 2. COPY ... REPLACING 패턴과 Java의 차이

COBOL의 `COPY ... REPLACING` 은 **C 언어의 `#define` 매크로** 또는 **Java 코드 생성(code generation)** 과 유사하다. 컴파일러가 소스를 전처리할 때 텍스트를 치환한다.

```cobol
* 사용 예 (추측 — 실제 호출 프로그램에서):
COPY CSSETATY REPLACING
    ==(TESTVAR1)== BY ==ACCTID==
    ==(SCRNVAR2)== BY ==ACCTIDD==
    ==(MAPNAME3)== BY ==COACTUPO==.
```

Java에는 이런 컴파일 타임 텍스트 치환이 없다. 대신:
- **Generics + 람다**: 타입 안전한 파라미터화
- **Template Method 패턴**: 공통 검증 흐름, 서브클래스가 필드별로 오버라이드
- **Annotation 기반 Bean Validation**: `@NotBlank`, `@NotNull` + 커스텀 `ConstraintValidator`

### 3. `CDEMO-PGM-REENTER` 와 HTTP 요청 라이프사이클 비교

CICS pseudo-conversational 모델에서 `CDEMO-PGM-REENTER`는 "이 화면이 이미 한 번 사용자에게 표시된 적이 있다"는 세션 상태이다.

| CICS | HTTP/Spring MVC |
|---|---|
| 첫 XCTL → 화면 진입 | GET /form |
| RETURN + COMMAREA | 서버 세션 또는 폼 히든 필드 |
| `CDEMO-PGM-REENTER = TRUE` | POST 요청 (사용자가 Submit 누름) |
| 속성 RED 설정 | `BindingResult.hasErrors()` → 모델에 오류 추가 |

### 4. DFHRED 상수

`DFHRED`는 CICS가 `DFHBMSCA` copybook에 정의하는 색상 상수이다. IBM 3270 터미널 프로토콜에서 색상은 1바이트 Extended Attribute로 전송된다(`X'F2'` = RED). 현대화 시 웹/앱 UI에서는 CSS 클래스나 색상 코드(`#FF0000`)로 대체한다.

### 5. 주의 사항 및 마이그레이션 체크리스트

- **미완성 소스 가능성(추측)**: 파일의 주석 라인(`* Set (TESTVAR1) to red if in error and * if blankACSHLIM`)에 `ACSHLIM`이 붙어 있어, 원본 소스에서 복사 중 잘린 것으로 보인다. 전체 사용 컨텍스트는 이 파일을 COPY하는 프로그램 목록을 검색해 확인해야 한다.
- **재진입 조건 보존**: Java 전환 시 "첫 진입에는 오류 표시 안 함" 동작을 반드시 보존해야 UX가 동일하게 유지된다.
- **88-level 플래그 매핑**: `FLG-...-NOT-OK`, `FLG-...-BLANK` 조건명들은 Java `enum` 또는 `boolean` 필드로 1:1 변환 가능하다.
- **공백과 `null`의 차이**: COBOL에서 "공백"은 PIC X 필드가 모두 SPACE(X'40')인 상태이며 `null`과 다르다. Java 변환 시 `String.isBlank()` (Java 11+) 으로 매핑하되, `null` 체크도 반드시 추가해야 한다.
