# CSMSG01Y — 공통 UI 메시지 텍스트 상수

- **유형**: Copybook
- **한 줄 요약**: 온라인(CICS) 화면에서 공통으로 표시되는 안내 메시지 2개를 50바이트 고정 길이 문자열 상수로 정의하는 공유 상수 copybook.

---

## 기능 설명

`CSMSG01Y`는 CardDemo 애플리케이션의 모든 온라인 프로그램이 공유하는 **화면 출력용 메시지 상수** 모음이다.
PROCEDURE DIVISION 없이 DATA DIVISION 선언만 존재하며, `COPY CSMSG01Y` 문으로 포함하면 `CCDA-COMMON-MESSAGES` 구조체가 해당 프로그램의 WORKING-STORAGE에 삽입된다.

정의된 메시지는 두 가지다.

1. **감사 메시지** (`CCDA-MSG-THANK-YOU`, 라인 18–19): 사용자가 정상 종료하거나 로그아웃할 때 화면 하단에 표시하는 "Thank you" 문구.
2. **잘못된 키 메시지** (`CCDA-MSG-INVALID-KEY`, 라인 20–21): 사용자가 정의되지 않은 기능키(PF 키 등)를 누를 때 경고로 표시하는 문구.

두 필드 모두 `VALUE` 절로 컴파일 시점에 초기화되므로 런타임에 별도 초기화 코드가 필요 없다.

---

## 필드 레이아웃

| 레벨 | 필드명 | PIC / USAGE | 바이트 | 의미 |
|------|--------|-------------|--------|------|
| 01 | `CCDA-COMMON-MESSAGES` | 그룹 | 100 | 공통 메시지 컨테이너 |
| 05 | `CCDA-MSG-THANK-YOU` | `PIC X(50)` | 50 | 정상 종료/로그아웃 시 표시 감사 메시지. VALUE `'Thank you for using CardDemo application...      '` (후행 공백 6자 포함, 총 50자). |
| 05 | `CCDA-MSG-INVALID-KEY` | `PIC X(50)` | 50 | 잘못된 기능키 입력 시 표시 경고 메시지. VALUE `'Invalid key pressed. Please see below...         '` (후행 공백 9자 포함, 총 50자). |

**참고사항:**
- 88-level 조건명 없음.
- `REDEFINES` 없음.
- `OCCURS` 없음.
- 두 필드의 문자열은 모두 50자로 우측 공백 패딩(SPACE padding)되어 있다. 이는 CICS 화면의 고정 길이 필드에 MOVE 후 별도 TRIM 없이 바로 출력하기 위한 COBOL 관용구다.

---

## 의존성

- **COPY (중첩 카피북)**: 없음
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**: 없음
- **트랜잭션 ID 또는 EXEC PGM**: 없음

---

## Java/현대화 노트

### 직접 대응: Java 문자열 상수 인터페이스 또는 열거형

COBOL의 `VALUE` 절로 초기화된 `PIC X(50)` 필드는 Java의 `public static final String` 상수와 동일한 역할을 한다.

```java
// COBOL COPY CSMSG01Y의 Java 등가 표현
public final class CardDemoMessages {

    private CardDemoMessages() {} // 인스턴스화 방지

    /**
     * CCDA-MSG-THANK-YOU (PIC X(50))
     * 정상 종료 또는 로그아웃 시 표시
     */
    public static final String THANK_YOU =
        "Thank you for using CardDemo application...";

    /**
     * CCDA-MSG-INVALID-KEY (PIC X(50))
     * 정의되지 않은 기능키 입력 시 표시
     */
    public static final String INVALID_KEY =
        "Invalid key pressed. Please see below...";
}
```

### 마이그레이션 시 주의점

**1. 후행 공백 처리**

COBOL VALUE 문자열은 50바이트 고정 길이를 맞추기 위해 후행 공백이 삽입되어 있다(라인 19: `'Thank you for using CardDemo application...      '`, 라인 21: `'Invalid key pressed. Please see below...         '`). 원본 메인프레임 화면(CICS BMS)은 고정 길이 필드에 직접 MOVE하므로 이 공백이 필요하지만, Java/현대 UI에서는 `.trim()` 또는 `String.strip()`으로 제거해야 한다.

**2. 문자셋 변환**

메인프레임 상에서 이 값들은 EBCDIC으로 저장된다. 현대화 시 ASCII/UTF-8로 변환은 단순하지만, 점(`...`) 문자가 멀티바이트 유니코드 줄임표(`…`, U+2026)로 대체될 경우 UI 표시 차이가 생길 수 있다 — 원본 텍스트 그대로 ASCII 점 세 개를 유지하는 것을 권장한다.

**3. 단순 상수 — 복잡성 없음**

이 copybook에는 `REDEFINES`, `OCCURS`, `COMP-3`, 조건명(88-level) 등 마이그레이션 복잡도를 높이는 요소가 전혀 없다. 1:1 상수 매핑으로 완전히 대체 가능하다.

**4. 사용 패턴 (추측)**

이 copybook을 COPY하는 CO* 온라인 프로그램들은 잘못된 키 처리 단락(예: `PROCESS-INVALID-KEY` 유사 단락)에서 `MOVE CCDA-MSG-INVALID-KEY TO <화면필드>` 패턴으로 메시지를 화면 맵에 쓴다. Java 웹/TUI 마이그레이션에서는 이를 응답 모델 또는 뷰 모델의 `errorMessage` 필드에 대입하는 패턴으로 치환한다.
