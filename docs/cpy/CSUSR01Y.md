# CSUSR01Y — 사용자 보안 레코드 레이아웃

- **유형**: Copybook
- **한 줄 요약**: VSAM KSDS 파일 `USRSEC`의 80바이트 레코드를 정의하는 공유 스키마로, 사용자 ID·이름·평문 비밀번호·권한 타입을 담는다.

---

## 기능 설명

`CSUSR01Y`는 CardDemo 애플리케이션의 사용자 인증·권한 정보를 저장하는 VSAM KSDS 파일 `USRSEC`의 레코드 레이아웃을 정의한다. 온라인(CICS) 로그인 처리 프로그램이 이 copybook을 통해 사용자 자격증명을 읽고, 사용자 타입(`A`=관리자, `U`=일반 사용자)에 따라 메뉴 분기 경로를 결정한다.

레코드 전체 길이는 고정 80바이트(DISPLAY 기준)이며, 80번째 바이트까지 명시적 FILLER(`PIC X(23)`)로 패딩되어 VSAM RRDS 또는 KSDS 고정 레코드 길이 요건을 충족한다.

---

## 필드 레이아웃

01레벨 이름: **`SEC-USER-DATA`**  
총 길이: **80바이트** (8 + 20 + 20 + 8 + 1 + 23 = 80)

| 필드명 | 오프셋 (1-based) | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|---|
| `SEC-USR-ID` | 1–8 | `PIC X(08)` | 8 | 사용자 로그인 ID. VSAM KSDS의 기본 키(prime key). 공백 패딩(우측). |
| `SEC-USR-FNAME` | 9–28 | `PIC X(20)` | 20 | 사용자 이름(First Name). 공백 패딩. |
| `SEC-USR-LNAME` | 29–48 | `PIC X(20)` | 20 | 사용자 성(Last Name). 공백 패딩. |
| `SEC-USR-PWD` | 49–56 | `PIC X(08)` | 8 | **평문 비밀번호.** 해싱·암호화 없이 EBCDIC 문자열로 저장됨 (보안 취약점, 현대화 필수 항목). |
| `SEC-USR-TYPE` | 57 | `PIC X(01)` | 1 | 사용자 권한 타입. `'A'` = 관리자(Admin), `'U'` = 일반 사용자(User). 88-level 조건명은 이 copybook에 없고, 이를 참조하는 `COCOM01Y`의 `CDEMO-USR-TYPE` 필드에서 `88 CDEMO-USRTYP-ADMIN VALUE 'A'`로 선언됨. |
| `SEC-USR-FILLER` | 58–80 | `PIC X(23)` | 23 | 예약 패딩(미사용). 향후 필드 추가 여지. |

> **스토리지 타입 주의**: 모든 필드가 `PIC X`(영숫자 DISPLAY)이므로 EBCDIC 인코딩. ASCII 환경으로 마이그레이션 시 문자 변환이 필요하다.  
> **COMP / COMP-3**: 이 레코드에는 없음.  
> **REDEFINES / OCCURS**: 없음.  
> **88-level**: 없음 (권한 타입 조건은 `COCOM01Y`에서 처리).

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — 이 copybook 자체가 다른 copybook을 COPY하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음 — 데이터 구조 정의 전용이며 실행 가능 코드를 포함하지 않는다.
- **데이터셋/파일/DB 테이블**: `USRSEC` (VSAM KSDS, 80바이트 고정 레코드, 키: `SEC-USR-ID` 8바이트). 온라인 프로그램에서는 DD명 `USRSEC`로 CICS FILE CONTROL에 등록됨. (추측: 배치 프로그램에서도 동일 파일을 SELECT … ASSIGN TO USRSEC로 참조할 수 있음.)
- **트랜잭션 ID 또는 EXEC PGM**: 없음 — copybook이므로 직접 실행되지 않는다.

---

## Java/현대화 노트

### 1. Java DTO 매핑

```java
/**
 * CSUSR01Y SEC-USER-DATA 레코드 대응 DTO.
 * VSAM USRSEC 파일의 80바이트 고정 레코드를 Java 객체로 표현.
 *
 * 주의: 원본 COBOL은 평문 비밀번호를 저장함 — 반드시 BCrypt 등으로 교체할 것.
 */
public class SecUserData {
    // SEC-USR-ID  PIC X(08) — VSAM KSDS prime key
    private String userId;          // 최대 8자, 공백 우측 패딩 제거 후 저장 권장

    // SEC-USR-FNAME PIC X(20)
    private String firstName;       // 최대 20자

    // SEC-USR-LNAME PIC X(20)
    private String lastName;        // 최대 20자

    // SEC-USR-PWD PIC X(08) — !! 평문 !!
    // 현대화: String rawPassword 대신 BCrypt 해시로 교체
    private String passwordHash;    // 마이그레이션 후 BCrypt 60자 해시

    // SEC-USR-TYPE PIC X(01) — 'A' 또는 'U'
    private UserType userType;

    // SEC-USR-FILLER PIC X(23) — 직렬화/역직렬화 시 무시
    // private byte[] filler;  // 필요 시 바이트 정합성 유지용으로만 사용

    public enum UserType {
        ADMIN("A"),   // COBOL 'A' — COCOM01Y 88-level CDEMO-USRTYP-ADMIN
        USER("U");    // COBOL 'U' — COCOM01Y 88-level CDEMO-USRTYP-USER (추측)

        private final String code;
        UserType(String code) { this.code = code; }
        public String getCode() { return code; }

        public static UserType fromCode(String code) {
            return Arrays.stream(values())
                .filter(t -> t.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown user type: " + code));
        }
    }
}
```

### 2. COBOL → Java 개념 대응표

| COBOL 구성 요소 | Java/현대 개념 |
|---|---|
| `01 SEC-USER-DATA` | `class SecUserData` (DTO / JPA `@Entity`) |
| `PIC X(08)` 필드 | `String` (단, 고정 길이 패딩 제거 필요: `.trim()` 또는 `StringUtils.stripTrailing()`) |
| `SEC-USR-TYPE PIC X(01)` | `enum UserType { ADMIN, USER }` |
| 88-level (COCOM01Y) `CDEMO-USRTYP-ADMIN VALUE 'A'` | `userType == UserType.ADMIN` |
| VSAM KSDS (SEC-USR-ID 키) | JPA `@Id` + `@Column(length=8)` 또는 Redis Key-Value |
| `SEC-USR-FILLER PIC X(23)` | 직렬화 시 무시(skipped), 바이트 오프셋 정합성이 필요하면 `@JsonIgnore` + 별도 바이트 버퍼 유지 |

### 3. 보안 취약점 — 평문 비밀번호 (라인 21: `SEC-USR-PWD PIC X(08)`)

이 필드는 8자 평문 비밀번호를 EBCDIC 문자열로 VSAM 파일에 직접 저장한다. 현대화 시 반드시 아래 전략을 적용해야 한다:

```java
// 현대화 예시: Spring Security + BCrypt
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();  // BCrypt 강도=10 기본값
}

// 마이그레이션 스크립트: VSAM 덤프 읽어 해시로 교체
String plainText = rawRecord.getPassword().trim();  // 원본 8자 평문
String hashed = passwordEncoder().encode(plainText);
user.setPasswordHash(hashed);
// 이후 VSAM 원본 파일의 SEC-USR-PWD는 폐기 또는 임시 난수로 덮어쓰기
```

### 4. 고정 길이 레코드 파싱 (EBCDIC→UTF-8)

```java
// VSAM 파일을 바이트로 읽어 SEC-USER-DATA로 역직렬화 예시 (IBM037 = EBCDIC US)
Charset ebcdic = Charset.forName("IBM037");

byte[] record = new byte[80];  // 고정 80바이트
inputStream.read(record);

SecUserData user = new SecUserData();
user.setUserId(new String(record,  0, 8, ebcdic).trim());
user.setFirstName(new String(record, 8, 20, ebcdic).trim());
user.setLastName(new String(record, 28, 20, ebcdic).trim());
// 평문 비밀번호는 읽은 즉시 해싱 후 원본 폐기
String raw = new String(record, 48, 8, ebcdic).trim();
user.setPasswordHash(passwordEncoder().encode(raw));
user.setUserType(UserType.fromCode(new String(record, 56, 1, ebcdic)));
// record[57..79] = FILLER, 무시
```

### 5. 연관 컴포넌트와의 관계

- `COCOM01Y.CDEMO-USR-TYPE` 필드가 이 레코드의 `SEC-USR-TYPE`을 세션 COMMAREA로 복사하여 전달한다. Java로 치면 로그인 성공 후 `HttpSession`에 `UserType` enum을 저장하는 것과 동일하다.
- 온라인 로그인 프로그램(추측: `COSGN00C`)이 `SEC-USR-ID` + `SEC-USR-PWD`를 입력값과 직접 문자열 비교(`EVALUATE` 또는 `IF`)로 인증한다. (추측: 소스 미확인)
- 관리자(A) 로그인 시 `COADM02Y` 메뉴 테이블, 일반 사용자(U) 로그인 시 `COMEN02Y` 메뉴 테이블로 라우팅된다 (`copybook_record_layouts.md` 참조).
