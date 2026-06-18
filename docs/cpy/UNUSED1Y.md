# UNUSED1Y — 미사용 사용자 자격증명 레이아웃 (더미/예비 레코드)

- **유형**: Copybook
- **한 줄 요약**: 사용자 ID·이름·비밀번호·유형을 담는 80바이트 고정 길이 레코드 구조를 정의하지만, 코드베이스 전체에서 `COPY UNUSED1Y`로 참조하는 프로그램이 존재하지 않아 실제로는 사용되지 않는 예비(더미) 레이아웃이다.

---

## 기능 설명

`UNUSED1Y`는 CardDemo 시스템의 `app/cpy/` 디렉터리에 위치한 copybook으로, 01-레벨 그룹 항목 `UNUSED-DATA` 아래에 사용자 인증 관련 필드 5개와 FILLER 1개를 정의한다(소스 1–7번 라인).

구조적으로는 실제 사용자 보안 레코드 copybook인 `CSUSR01Y`(`SEC-USER-DATA`, 에이전트 메모리 `copybook_record_layouts.md` 참조)와 유사한 필드 집합을 갖는다. `CSUSR01Y`에는 `SEC-USR-ID PIC X(09)`, `SEC-USR-PWD PIC X(08)`, `SEC-USR-TYPE PIC X(01)` 등이 정의되어 있으며, `UNUSED1Y`의 `UNUSED-ID(08)`, `UNUSED-PWD(08)`, `UNUSED-TYPE(01)` 필드와 역할이 겹친다.

이 copybook이 실제로 사용되지 않는 이유에 대한 명시적 주석은 없으나, 다음 두 가지 가능성을 추측할 수 있다.

1. **설계 초안**: `CSUSR01Y` 작성 전 초기 설계 시도로 작성된 뒤 버려진 임시 레이아웃일 가능성.
2. **마이그레이션 테스트용 플레이스홀더**: 향후 데이터 레이아웃 변경이나 A/B 테스트를 위해 예약된 구조일 가능성.

파일명 자체(`UNUSED`)가 미사용 상태를 명시하고 있으며, 전체 코드베이스(`app/cbl/`, `app/cpy/`, JCL 등)를 대상으로 `COPY UNUSED1Y` 참조를 검색한 결과 **단 한 건도 발견되지 않았다**. 소스 버전 주석(9번 라인 `CardDemo_v1.0-56-gd8e5ebf-109`, 2022-08-19)은 코드베이스의 최신 빌드 태그 중 하나로, 파일 자체는 최신 커밋까지 유지된 채 존재하지만 여전히 참조되지 않는다.

Java 관점에서 이 copybook은 `UserCredentials` DTO 클래스 초안 정도에 해당하며, 실제 사용자 보안 레코드는 `CSUSR01Y`(→ `SecUserData` 클래스)가 담당한다.

---

## 필드 레이아웃

| 순번 | 레벨 | 필드명 | PIC / USAGE | 바이트 | 의미 및 Java 매핑 |
|------|------|--------|-------------|--------|-------------------|
| 1 | 01 | `UNUSED-DATA` | GROUP | 80 | 루트 그룹 항목. Java: `class UnusedData` |
| 2 | 05 | `UNUSED-ID` | `PIC X(08)` DISPLAY | 8 | 사용자 식별자. Java: `String userId` (최대 8자, 고정 길이 공백 패딩). `CSUSR01Y`의 `SEC-USR-ID PIC X(09)`와 유사하나 1바이트 짧음 |
| 3 | 05 | `UNUSED-FNAME` | `PIC X(20)` DISPLAY | 20 | 사용자 이름(First Name). Java: `String firstName` (최대 20자) |
| 4 | 05 | `UNUSED-LNAME` | `PIC X(20)` DISPLAY | 20 | 사용자 성(Last Name). Java: `String lastName` (최대 20자) |
| 5 | 05 | `UNUSED-PWD` | `PIC X(08)` DISPLAY | 8 | 비밀번호. Java: `String password` — 평문 저장 구조임에 유의. 메인프레임 EBCDIC 문자셋으로 저장됨 |
| 6 | 05 | `UNUSED-TYPE` | `PIC X(01)` DISPLAY | 1 | 사용자 유형 코드. Java: `char userType` 또는 enum. `CSUSR01Y`에서는 `'A'`=관리자, `'U'`=일반 사용자로 정의되나(에이전트 메모리 참조), 이 필드의 실제 코드값은 참조 프로그램이 없어 확인 불가(추측) |
| 7 | 05 | `UNUSED-FILLER` | `PIC X(23)` DISPLAY | 23 | 예약 패딩. Java에서는 무시 또는 `byte[] reserved = new byte[23]` |

**총 바이트 합계**: 8 + 20 + 20 + 8 + 1 + 23 = **80바이트**

88-레벨 조건명, REDEFINES, OCCURS, COMP-3 없음 — 모든 필드 단순 `PIC X(n)` DISPLAY 알파뉴메릭.

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — `UNUSED1Y` 자체는 다른 copybook을 `COPY`하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음 — copybook은 데이터 구조 선언만 담으며 실행 코드가 없다. 더불어 이 copybook을 `COPY`하는 프로그램도 전체 코드베이스에서 발견되지 않는다.
- **데이터셋/파일/DB 테이블**: 없음 — 대응하는 VSAM 파일이나 DB 테이블이 코드베이스에 정의되어 있지 않다. 실제 사용자 보안 레코드는 `CSUSR01Y` ↔ VSAM `USRSEC`(레코드 길이 80) 조합이 담당한다(에이전트 메모리 `copybook_record_layouts.md` 참조).
- **트랜잭션 ID 또는 EXEC PGM**: 없음

---

## Java/현대화 노트

### 1. 이 copybook의 마이그레이션 권고

**마이그레이션 대상에서 제외를 권장한다.** 전체 코드베이스 검색 결과 `COPY UNUSED1Y`를 사용하는 프로그램이 단 한 건도 없으므로, Java 변환 시 이에 대응하는 클래스를 작성할 필요가 없다. 단, 삭제 전 다음 사항을 확인해야 한다.

- JCL이나 동적 `COPY` 문(리터럴이 아닌 변수 치환)으로 참조하는 경우가 있는지 빌드 스크립트 전체 검토
- 코드베이스 외부(다른 시스템, 공유 카피북 라이브러리)에서 참조 여부 확인

### 2. 실제 사용자 보안 구조 비교

이 copybook이 대응하려 한 것으로 추정되는 실제 레이아웃은 `CSUSR01Y`이다.

```java
// UNUSED1Y 필드 → 실제 CSUSR01Y(SEC-USER-DATA) 필드 대응
// UNUSED-ID   PIC X(08)  ↔  SEC-USR-ID   PIC X(09)  ← 길이 차이 주의
// UNUSED-FNAME PIC X(20) ↔  SEC-USR-FNAME PIC X(20) ← (CSUSR01Y에 없으면 추측)
// UNUSED-LNAME PIC X(20) ↔  SEC-USR-LNAME PIC X(20)
// UNUSED-PWD  PIC X(08)  ↔  SEC-USR-PWD  PIC X(08)
// UNUSED-TYPE PIC X(01)  ↔  SEC-USR-TYPE PIC X(01)  'A'=admin, 'U'=user
```

마이그레이션 시 사용자 엔티티는 `CSUSR01Y` 기반으로 설계하고, `UNUSED1Y`의 구조는 참고 자료로만 활용한다.

### 3. PIC X(n) DISPLAY — EBCDIC 주의사항

모든 필드가 `PIC X(n)` DISPLAY이므로 VSAM 파일에 EBCDIC 코드포인트로 저장된다. Java로 읽을 때 반드시 EBCDIC(Cp037 또는 Cp1047) → UTF-8 변환이 필요하다.

```java
// EBCDIC 바이트 배열 → Java String 변환 예시
byte[] rawBytes = /* VSAM 레코드에서 읽은 80바이트 */;
String unusedId = new String(rawBytes, 0, 8, Charset.forName("Cp1047")).trim();
```

### 4. 비밀번호 평문 저장 위험

`UNUSED-PWD PIC X(08)`는 비밀번호를 평문으로 저장하는 구조다. 실제 사용 중인 `CSUSR01Y`의 `SEC-USR-PWD`도 동일한 구조이며, 이는 레거시 메인프레임의 일반적인 관행이었다. Java 마이그레이션 시 반드시 bcrypt/Argon2 등 단방향 해시 저장 방식으로 전환해야 한다.

### 5. 고정 길이 문자열 처리

COBOL `PIC X(n)` 필드는 항상 n바이트 고정 길이로 공백 패딩(space padding)된다. Java에서 읽을 때 `.trim()`으로 후행 공백을 제거해야 실제 값을 얻을 수 있다.

```java
// FNAME 20바이트 공백 패딩 제거
String firstName = new String(rawBytes, 8, 20, Charset.forName("Cp1047")).trim();
```

---

*소스 버전: CardDemo_v1.0-56-gd8e5ebf-109 (2022-08-19, 소스 9번 라인)*
*미사용 확인: 코드베이스 전체 `COPY UNUSED1Y` 검색 결과 0건 (2026-06-18 기준)*
