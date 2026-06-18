# TCATBALF — 거래 카테고리 잔액 VSAM 파일 초기 적재

- **유형**: JCL
- **한 줄 요약**: 거래 카테고리 잔액 VSAM KSDS 파일(`TCATBALF`)을 삭제·재정의한 뒤, 동일 이름의 순차 플랫 파일(PS)에서 IDCAMS REPRO로 초기 데이터를 적재한다.

---

## 기능 설명

`TCATBALF`는 CardDemo의 야간 배치 데이터 준비 단계에서 실행되는 세 스텝짜리 초기화·적재 잡이다. 대상 파일(`AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS`)은 계정별·거래 유형별·카테고리별 잔액을 저장하는 VSAM KSDS로, `CBTRN02C`(거래 입력 배치)가 카테고리 잔액을 갱신할 때 읽고 쓰는 파일이고, `CBACT04C`(이자 계산 배치)가 드라이빙 파일로 순차 스캔하는 파일이다.

잡의 처리 순서는 아래와 같다.

1. **STEP05** — 기존 KSDS 클러스터가 있으면 삭제(`DELETE CLUSTER`). 실패해도 `SET MAXCC=0`으로 반환코드를 강제로 0으로 리셋해 후속 스텝이 무조건 실행되도록 한다. 처음 실행하는 환경처럼 파일이 없을 때도 잡이 중단되지 않기 위한 방어 코드이다.
2. **STEP10** — `DEFINE CLUSTER`로 새 KSDS를 정의한다. 고정 길이 50바이트 레코드, 키 길이 17바이트(오프셋 0), 볼륨 `AWSHJ1`, 1차 1실린더·2차 5실린더를 할당한다. `SHAREOPTIONS(2 3)`은 동일 볼륨 내에서 여러 잡이 동시에 읽을 수 있되 쓰기는 배타적임을 의미한다.
3. **STEP15** — `REPRO`로 플랫 파일(`AWS.M2.CARDDEMO.TCATBALF.PS`)의 레코드를 방금 생성한 KSDS로 복사해 초기 시드 데이터를 적재한다.

Java/Spring 관점에서 보면 이 잡 전체는 "빈 테이블을 DROP→CREATE한 뒤 CSV를 INSERT하는 Flyway 마이그레이션 스크립트"와 동일한 역할이다.

---

## 스텝 구성

| 스텝명 | EXEC PGM | 역할 |
|--------|----------|------|
| STEP05 | `IDCAMS` | 기존 KSDS 클러스터 `DELETE`. `SET MAXCC=0`으로 "파일 없음" 오류를 무시하고 잡 계속 진행 |
| STEP10 | `IDCAMS` | KSDS `DEFINE CLUSTER` — 키 17바이트, 레코드 50바이트 고정, 볼륨 AWSHJ1, 실린더 1/5 |
| STEP15 | `IDCAMS` | 플랫 파일(PS) → KSDS `REPRO` (초기 데이터 적재) |

> 세 스텝 모두 IBM 유틸리티 프로그램 `IDCAMS`를 사용하며, 별도 COBOL 프로그램은 없다. `SYSPRINT DD SYSOUT=*`는 IDCAMS 실행 로그를 시스템 출력(SPOOL)으로 내보내는 표준 관례이다.

---

## 의존성

### COPY (PROC/INCLUDE)
- 없음. 이 잡은 인라인 SYSIN만 사용하며 외부 카탈로그 프로시저나 INCLUDE 멤버를 참조하지 않는다.

### 호출 프로그램 (EXEC PGM)
- `IDCAMS` — IBM 시스템 유틸리티. VSAM 클러스터 관리(`DELETE`, `DEFINE CLUSTER`)와 데이터 복사(`REPRO`)를 처리하는 표준 메인프레임 유틸리티로, JDK의 `java.nio.file` 파일 조작 API에 해당한다.

### 데이터셋/파일/DB 테이블

| DD명 | 데이터셋 이름 | 역할 |
|------|--------------|------|
| (SYSIN 인라인) | `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS` | 대상 KSDS 클러스터(DATA + INDEX 컴포넌트 포함) |
| `TCATBAL` | `AWS.M2.CARDDEMO.TCATBALF.PS` | 원본 플랫 순차 파일(DISP=SHR) — 소스 데이터 |
| `TCATBALV` | `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS` | 대상 KSDS(DISP=OLD) — REPRO 출력 |
| `SYSPRINT` | `SYSOUT=*` | IDCAMS 실행 로그(각 스텝마다 선언) |

**레코드 레이아웃**: 50바이트 고정 레코드. copybook `CVTRA01Y`(`app/cpy/CVTRA01Y.cpy`)의 `TRAN-CAT-BAL-RECORD`로 정의된다. 키(17바이트)는 `TRANCAT-ACCT-ID`(계정 ID, 11바이트) + `TRANCAT-TYPE-CD`(거래 유형 코드, 2바이트) + `TRANCAT-CAT-CD`(카테고리 코드, 4바이트)의 복합 키다.

```
TRAN-CAT-BAL-RECORD (50바이트)
├── TRANCAT-ACCT-ID     PIC 9(11)      -- 계정 ID (키 첫 11바이트)
├── TRANCAT-TYPE-CD     PIC X(2)       -- 거래 유형 코드 (키 +2)
├── TRANCAT-CAT-CD      PIC X(4)       -- 카테고리 코드 (키 +4)
└── TRAN-CAT-BAL        PIC S9(9)V99   -- 카테고리별 누적 잔액 (부호포함 10진수)

-- Java 대응: BigDecimal (scale=2, RoundingMode 명시 필수)
```

### 선행/후행 잡

| 구분 | 잡명 | 설명 |
|------|------|------|
| 선행 잡 (일반적) | `CLOSEFIL` | CICS가 열어 둔 VSAM 파일을 닫아야 이 잡이 DISP=OLD로 파일에 접근 가능. CICS가 실행 중이라면 필수 선행 조건 |
| 후행 잡 | `POSTTRAN` (`CBTRN02C`) | TCATBALF를 I-O 랜덤 모드로 열어 카테고리 잔액을 갱신하는 거래 입력 배치 |
| 후행 잡 | `INTCALC` (`CBACT04C`) | TCATBALF를 드라이빙 파일로 순차 스캔해 월 이자를 계산하는 배치 |
| 후행 잡 (일반적) | `OPENFIL` | 배치 완료 후 CICS가 VSAM 파일을 다시 열어 온라인 서비스 재개 |

> 야간 배치 전체 시퀀스: `CLOSEFIL` → **TCATBALF (이 잡)** → `POSTTRAN` → `INTCALC` → `TRANBKP` → `COMBTRAN` → `TRANIDX` → `OPENFIL`

---

## Java/현대화 노트

### 1. IDCAMS DELETE + SET MAXCC=0 패턴

```jcl
   DELETE AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS CLUSTER
   SET    MAXCC = 0
```

`SET MAXCC=0`은 JCL에서 "이전 명령의 오류코드를 무시하라"는 뜻이다. Java로 옮기면 아래와 같다.

```java
// Java 대응: 삭제 실패를 무시하는 방어적 초기화
try {
    deleteVsamFile("TCATBALF.VSAM.KSDS");
} catch (FileNotFoundException ignored) {
    // 파일 없음은 정상 — SET MAXCC=0 에 해당
}
createVsamFile(...);
```

Flyway/Liquibase에서는 `IF EXISTS`(DROP TABLE IF EXISTS)로 동일한 의미를 표현한다.

### 2. DEFINE CLUSTER 파라미터 → 테이블 DDL 매핑

| IDCAMS 파라미터 | Java/RDBMS 대응 | 비고 |
|----------------|----------------|------|
| `KEYS(17 0)` | PRIMARY KEY (오프셋 0, 길이 17) | 복합 키: ACCT-ID(11) + TYPE-CD(2) + CAT-CD(4) |
| `RECORDSIZE(50 50)` | CHAR(50) 고정 행 | 최소=최대=50이므로 FIXED 레코드 |
| `CYLINDERS(1 5)` | 초기 용량 힌트 / tablespace 크기 | 1차 1실린더, 부족 시 5실린더씩 확장 |
| `SHAREOPTIONS(2 3)` | READ UNCOMMITTED (크로스 리전 공유) | 동일 시스템에서 다중 리더 허용, 쓰기는 배타 |
| `ERASE` | DROP 시 물리 오버라이트 | 보안 삭제 — Java에서는 별도 구현 필요 |
| `INDEXED` | B-Tree 인덱스 기반 KSDS | 키 순서로 정렬된 HashMap이 아닌 TreeMap/B-Tree |

### 3. REPRO = 대량 INSERT (Bulk Load)

```jcl
   REPRO INFILE(TCATBAL) OUTFILE(TCATBALV)
```

`REPRO`는 소스 파일의 레코드를 키 순서로 KSDS에 삽입한다. 소스 플랫 파일이 키 오름차순으로 정렬되어 있어야 한다(정렬되지 않으면 IDCAMS가 오류를 반환한다).

```java
// Java 대응: JdbcBatchItemWriter 또는 직접 bulk insert
List<TransactionCategoryBalance> records = flatFileReader.readAll();
records.sort(Comparator.comparing(TransactionCategoryBalance::getCompositeKey));
repository.saveAll(records); // bulk INSERT
```

Spring Batch로 마이그레이션 시 `FlatFileItemReader`(PS 플랫 파일) + `JpaItemWriter`(DB) 패턴이 자연스럽다.

### 4. TRAN-CAT-BAL 필드의 Java 매핑 주의사항

`TRAN-CAT-BAL PIC S9(9)V99`는 packed decimal(COMP-3 여부는 copybook 확인 필요 — DISPLAY면 11바이트, COMP-3이면 6바이트)이다. 이 필드를 Java `double`/`float`으로 매핑하면 이자 계산 시 부동소수점 오차가 발생한다.

```java
// 올바른 Java 매핑
BigDecimal tranCatBal = new BigDecimal("12345678.99"); // scale=2 고정
// CBACT04C 이자 계산 공식: (잔액 × 연이율%) / 1200
BigDecimal monthlyInterest = tranCatBal
    .multiply(annualInterestRate)
    .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
```

### 5. 마이그레이션 시 고려사항

- **고정 길이 레코드**: 플랫 파일(PS)의 레코드는 LRECL=50, RECFM=FB. Java `FlatFileItemReader`에서 `FixedLengthTokenizer`로 파싱해야 한다.
- **EBCDIC 인코딩**: 메인프레임 원본 데이터는 EBCDIC이므로 `data/EBCDIC/` 아래 파일을 직접 읽을 때는 `Charset.forName("Cp1047")` 등 IBM EBCDIC 코드페이지를 지정해야 한다. `data/ASCII/` 아래 파일은 ASCII로 변환된 버전이다.
- **SHAREOPTIONS(2 3)**: 배치와 온라인이 동시에 이 파일을 접근하는 상황을 상정한 설정이다. Java로 전환 시 낙관적 잠금(Optimistic Locking)이나 행 단위 락(`SELECT FOR UPDATE`) 전략을 별도로 설계해야 한다.
- **복합 기본 키**: `ACCT-ID + TYPE-CD + CAT-CD` 조합이 PK다. JPA로 매핑 시 `@EmbeddedId` 또는 `@IdClass` 패턴을 사용한다.

```java
@Embeddable
public class TransactionCategoryKey implements Serializable {
    @Column(name = "acct_id", length = 11)
    private String acctId;           // TRANCAT-ACCT-ID PIC 9(11)
    @Column(name = "type_cd", length = 2)
    private String typeCd;           // TRANCAT-TYPE-CD PIC X(2)
    @Column(name = "cat_cd", length = 4)
    private String catCd;            // TRANCAT-CAT-CD  PIC X(4)
}
```

---

*소스 기준: `app/jcl/TCATBALF.jcl` (CardDemo_v1.0-15-g27d6c6f-68, 2022-07-19)*
*레코드 레이아웃 참조: `app/cpy/CVTRA01Y.cpy` (`TRAN-CAT-BAL-RECORD`)*
