# CVTRA03Y — 거래 유형 레코드 레이아웃

- **유형**: Copybook
- **한 줄 요약**: VSAM KSDS 파일 TRANTYPE의 레코드 레이아웃을 정의하는 copybook으로, 2자리 거래 유형 코드(TRAN-TYPE)와 50자리 설명(TRAN-TYPE-DESC)을 담는 60바이트 고정 레코드 구조이다.

---

## 기능 설명

CVTRA03Y는 CardDemo 시스템에서 사용하는 **거래 유형 참조 테이블**의 레코드 구조를 선언한다. 이 copybook이 정의하는 `TRAN-TYPE-RECORD`는 VSAM KSDS 파일 `TRANTYPE`(DD명)에 저장된 각 레코드를 메인 메모리에 올릴 때 사용하는 01 레벨 구조체이다.

이 레코드의 역할은 Java 애플리케이션에서 `Map<String, String> transactionTypeDescriptions`처럼 **코드 → 설명** 조회 테이블을 제공하는 것이다. 배치 프로그램 CBTRN03C는 거래 리포트를 생성할 때 각 거래의 `TRAN-TYPE-CD`로 이 KSDS를 RANDOM 읽기하여 사람이 읽을 수 있는 설명을 리포트에 출력한다(CBTRN03C 라인 189~366).

레코드 길이는 헤더 주석에 `RECLN = 60`으로 명시되어 있으며, 필드 합산(2 + 50 + 8 = 60바이트)과 정확히 일치한다(CVTRA03Y 라인 1~7).

---

## 필드 레이아웃

| 필드명 | 오프셋(1~) | PIC / USAGE | 바이트 | 의미 및 Java 대응 |
|---|---|---|---|---|
| `TRAN-TYPE` | 1 | `PIC X(02)` | 2 | VSAM KSDS **레코드 키**. 거래 유형 코드("PU", "SA" 등 2자리 EBCDIC 문자). Java: `String` (길이 2, 고정). CBTRN03C에서 `FD-TRAN-TYPE`(PIC X(02))이 이 키로 RANDOM READ를 수행한다(CBTRN03C 라인 42, 189). |
| `TRAN-TYPE-DESC` | 3 | `PIC X(50)` | 50 | 거래 유형 설명 문자열. SPACE 패딩 고정 50자. Java: `String`으로 매핑 시 `trim()` 필수. CBTRN03C 라인 366에서 리포트 출력 필드 `TRAN-REPORT-TYPE-DESC`로 MOVE된다. |
| `FILLER` | 53 | `PIC X(08)` | 8 | 사용하지 않는 예약 패딩. 레코드 길이를 60바이트로 맞추기 위한 공간. Java 매핑 시 무시해도 무방. |

**특이 구조 없음**: 이 레코드에는 REDEFINES, OCCURS, COMP-3, 88-level 조건명이 없다. 순수한 2-필드 플랫 구조이다.

---

## 의존성

- **COPY (중첩 카피북)**: 없음
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**:
  - `TRANTYPE` (DD명) — VSAM KSDS, RECORD KEY = `FD-TRAN-TYPE`(PIC X(02)). CBTRN03C의 ENVIRONMENT DIVISION에서 `SELECT TRANTYPE-FILE ASSIGN TO TRANTYPE`으로 선언(라인 39~43). 이 파일을 INPUT으로 OPEN하고 RANDOM READ 시 버퍼 구조체로 `TRAN-TYPE-RECORD`가 사용된다(라인 495).
  - COTRTLIC.cbl — `LIT-TRANTYPE-TABLE`(PIC X(30))을 통해 인메모리 테이블 방식으로 거래 유형 코드를 보유하며 CVTRA03Y를 직접 COPY하지 않고 자체 구조를 사용한다(라인 56). CVTRA03Y 레코드와 논리적으로 동일한 데이터를 다른 방식으로 처리한다(추측).
  - COTRTUPC.cbl — `WS-TRANTYPE-MASTER-READ-FLAG`(88 `FOUND-TRANTYPE-IN-TABLE`), 9000-READ-TRANTYPE 단락 등으로 거래 유형 조회를 수행하나 CVTRA03Y를 직접 COPY하지 않는다. 별도 내부 구조를 사용하는 것으로 보인다(추측).
- **트랜잭션 ID 또는 EXEC PGM**: 없음

---

## Java/현대화 노트

### 1. 레코드 구조 → Java DTO

```java
// TRAN-TYPE-RECORD 직접 대응
public record TransactionType(
    String typeCode,   // TRAN-TYPE   PIC X(02) — VSAM KSDS 키
    String description // TRAN-TYPE-DESC PIC X(50) — trim() 필수
) {}
```

COBOL 고정 길이 필드이므로 EBCDIC→ASCII 변환 후 `String.trim()` 또는 `String.stripTrailing()`을 반드시 적용해야 한다. 그렇지 않으면 설명 문자열 끝에 공백 8~50자가 붙어 표시된다.

### 2. VSAM KSDS → Java 참조 데이터 패턴

COBOL에서는 `READ TRANTYPE-FILE INTO TRAN-TYPE-RECORD KEY IS FD-TRAN-TYPE`으로 단건 RANDOM 조회한다(CBTRN03C 라인 495). Java 현대화 시 다음 두 가지 패턴이 적합하다.

**옵션 A — 데이터베이스 조회 (권장)**
```java
// TRANTYPE VSAM → TRAN_TYPE 테이블
@Repository
public interface TransactionTypeRepository
        extends JpaRepository<TransactionType, String> {
    // findById(typeCode) → Optional<TransactionType>
}
```

**옵션 B — 시작 시 전체 로드 (배치 처리에 적합)**
```java
@Component
public class TransactionTypeCache {
    private final Map<String, String> cache = new HashMap<>();

    @PostConstruct
    public void load() {
        // DB 또는 파일에서 전체 로드 후 typeCode→description 매핑
        repository.findAll()
                  .forEach(t -> cache.put(t.typeCode(), t.description().stripTrailing()));
    }

    public Optional<String> getDescription(String typeCode) {
        return Optional.ofNullable(cache.get(typeCode));
    }
}
```

CBTRN03C는 거래 건마다 RANDOM READ를 반복하므로(라인 190), Java 배치로 전환 시 옵션 B처럼 캐시를 사용하면 I/O 오버헤드를 줄일 수 있다.

### 3. 고정 레코드 길이 60바이트 처리

바이너리 파일 또는 레거시 EBCDIC 파일을 직접 읽어야 하는 경우, Java에서 60바이트 고정 레코드를 파싱할 때 다음 오프셋을 사용한다.

```java
// 바이트 오프셋 (0-based)
String typeCode  = new String(record,  0,  2, charset); // bytes 0–1
String typeDesc  = new String(record,  2, 50, charset); // bytes 2–51
// bytes 52–59: FILLER 8바이트 — 무시
```

`charset`은 z/OS 기본값 `Cp1047`(EBCDIC Latin-1) 또는 `IBM037`을 사용한다. 실제 코드 페이지는 JCL의 `CCSID` 파라미터 또는 시스템 설정을 확인해야 한다(추측).

### 4. FILLER 8바이트 — 향후 확장 공간 가능성

FILLER 8바이트는 레코드 길이 정렬용으로 보이며, 현재 어떤 프로그램도 이 영역을 읽거나 쓰지 않는다. Java 마이그레이션 시 이 필드는 DTO에 포함시키지 않아도 된다. 단, 레거시 파일과 바이트 수준 호환성을 유지해야 하는 경우 직렬화/역직렬화 시 60바이트 고정 패딩을 유지해야 한다.

### 5. 참조 정합성 고려

COBOL TRANTYPE 파일에는 외래 키 제약이 없다. CBTRN03C는 READ 실패 시 `'INVALID TRANSACTION TYPE'` 메시지를 출력하고 처리를 계속한다(라인 497). Java 현대화 시 명시적 유효성 검증 계층(`@NotBlank`, 존재 여부 체크)을 추가하는 것을 권장한다.
