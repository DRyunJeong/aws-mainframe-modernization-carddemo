# COTTL01Y — 화면 공통 타이틀 텍스트 상수

- **유형**: Copybook
- **한 줄 요약**: CardDemo 온라인 화면 전체에서 공유하는 애플리케이션 타이틀·감사 메시지 상수 3개를 고정 40바이트 문자열로 정의한 최소 레이아웃 copybook이다.

## 기능 설명

COTTL01Y는 CardDemo 온라인 화면의 **상단 타이틀 영역**에 표시할 텍스트를 중앙 집중 관리하기 위한 copybook이다. 프로그램 코드에 문자열 리터럴을 직접 박아 넣는 대신 이 copybook을 `COPY COTTL01Y`로 포함하면, 타이틀 문구 변경 시 단 한 곳만 수정하면 전체 화면에 일괄 반영된다(Java의 공유 상수 인터페이스 또는 `public static final String` 클래스와 동일한 역할).

구성은 단일 `01` 레벨 레코드 `CCDA-SCREEN-TITLE` 아래 3개의 `05` 레벨 필드로 이루어진다. 총 바이트 크기는 40 × 3 = **120바이트**이다.

소스 26번째 줄 주석에 버전 태그(`CardDemo_v1.0-15-g27d6c6f-68 Date: 2022-07-19`)가 있어, 이 copybook이 버전 관리 빌드 파이프라인으로 생성·관리되었음을 알 수 있다.

`CCDA-TITLE02`의 이전 값(`Credit Card Demo Application (CCDA)`)은 주석 처리되어 있고 현재 유효 값은 `CardDemo`이다(소스 21~22번째 줄). 이는 애플리케이션 리브랜딩 이력을 보여 준다.

## 필드 레이아웃

`01 CCDA-SCREEN-TITLE` (총 120바이트, DISPLAY)

| 필드명 | PIC / USAGE | 오프셋(바이트) | 의미 |
|---|---|---|---|
| `CCDA-TITLE01` | `PIC X(40)` | 0–39 | 화면 1행 타이틀: `'      AWS Mainframe Modernization       '` |
| `CCDA-TITLE02` | `PIC X(40)` | 40–79 | 화면 2행 타이틀: `'              CardDemo                  '` |
| `CCDA-THANK-YOU` | `PIC X(40)` | 80–119 | 화면 이탈 시 감사 메시지: `'Thank you for using CCDA application... '` |

**비고:**
- 세 필드 모두 `VALUE` 절로 컴파일 시 초기값이 고정된다. 런타임 변경 불가(상수 취급).
- `PIC X(40)`은 EBCDIC 40바이트 고정 문자열. 값 자체가 공백 패딩으로 40자를 채운다.
- 88-level, REDEFINES, OCCURS 없음.
- COMP/COMP-3 없음 — 전 필드 DISPLAY.

## 의존성

- **COPY (중첩 카피북)**: 없음
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**: 없음
- **트랜잭션 ID 또는 EXEC PGM**: 없음

## Java/현대화 노트

### 직접 대응 패턴

COBOL의 `01 CCDA-SCREEN-TITLE`에 VALUE로 고정된 필드들은 Java에서 공유 상수 클래스로 표현하는 것이 가장 자연스럽다.

```java
/**
 * CardDemo 화면 공통 타이틀 상수.
 * COTTL01Y copybook의 CCDA-SCREEN-TITLE에 대응.
 */
public final class CcdaScreenTitle {
    private CcdaScreenTitle() {}

    /** CCDA-TITLE01: PIC X(40), 오프셋 0 */
    public static final String TITLE01 = "      AWS Mainframe Modernization       ";

    /** CCDA-TITLE02: PIC X(40), 오프셋 40 */
    public static final String TITLE02 = "              CardDemo                  ";

    /** CCDA-THANK-YOU: PIC X(40), 오프셋 80 */
    public static final String THANK_YOU = "Thank you for using CCDA application... ";
}
```

### 고정 길이 vs 가변 길이

- COBOL의 `PIC X(40)`은 항상 정확히 40바이트(EBCDIC). Java `String`은 가변 길이이므로, CICS MQ·소켓 등을 통해 메인프레임과 바이트 스트림을 직접 교환하는 경우에는 `String.format("%-40s", TITLE01)` 또는 Apache Commons `StringUtils.rightPad()`로 공백 패딩을 유지해야 한다.
- 순수 UI 용도라면 패딩 제거 후 `.trim()`으로 사용해도 무방하다.

### EBCDIC 인코딩

- 메인프레임 파일에서 이 레코드를 읽을 때는 `Cp037`(IBM US EBCDIC) 코드페이지로 디코딩해야 한다.

```java
byte[] ebcdicBytes = /* VSAM/파일에서 읽은 120바이트 */;
String title01 = new String(ebcdicBytes, 0, 40, Charset.forName("Cp037"));
```

### 버전 관리

- 소스 주석의 버전 태그(`CardDemo_v1.0-15-g27d6c6f-68`)는 Git 태그 또는 빌드 파이프라인에서 주입된 것으로 보인다(추측). Java 마이그레이션 시 동일 문구를 `build.gradle` / `pom.xml`의 `projectVersion` 또는 `application.properties`로 옮기는 것을 권장한다.

### 리브랜딩 이력

- `CCDA-TITLE02`의 이전 값 `'  Credit Card Demo Application (CCDA)   '`이 주석으로 남아 있다(소스 21번째 줄). 마이그레이션 시 해당 주석 문자열을 별도 상수로 보존할 필요는 없다. 단, 문서화 목적으로 commit message에 이력을 남기는 것을 권장한다.
