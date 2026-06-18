# TRANCATG — 거래 카테고리 VSAM KSDS 정의 및 초기 적재 잡

- **유형**: JCL
- **한 줄 요약**: 거래 카테고리 코드·설명 마스터(KSDS)를 (재)생성하고 순차 플랫 파일에서 초기 데이터를 적재하는 1회성 환경 구성 잡.

---

## 기능 설명

`TRANCATG` 잡은 CardDemo 시스템에서 거래 카테고리(Transaction Category) 코드와 설명을 보관하는 VSAM KSDS(`AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS`)를 처음 구축하거나 완전 재구성할 때 사용하는 초기화 잡이다.

이 KSDS는 거래 유형(2자) + 카테고리 코드(4자리) 복합키로 색인되며, 고정 길이 60바이트 레코드(copybook `CVTRA04Y`의 `TRAN-CAT-RECORD`)를 저장한다. 배치 리포트 프로그램 `CBTRN03C`(잡 `TRANREPT`)가 이 파일을 RANDOM 조회(룩업)하여 거래 리포트에 카테고리 설명을 출력한다.

처리는 세 단계로 구성된다:

1. **STEP05**: 동일 이름의 KSDS가 이미 존재하면 삭제(idempotent 재실행 보장). 삭제 실패 시 `SET MAXCC = 0`으로 리턴코드를 강제 0으로 리셋해 후속 스텝이 중단되지 않도록 한다.
2. **STEP10**: KSDS를 새로 정의한다. 복합키 6바이트(`TRAN-TYPE-CD` 2B + `TRAN-CAT-CD` 4B), 고정 레코드 60바이트, 볼륨 `AWSHJ1`, 초기 1실린더·확장 5실린더로 할당된다.
3. **STEP15**: 소스 플랫 파일(`AWS.M2.CARDDEMO.TRANCATG.PS`)에서 KSDS로 데이터를 복사(REPRO)한다. IDCAMS의 REPRO는 순차 → KSDS 방향으로 레코드를 삽입하면서 자동으로 키 기준 정렬을 강제하지 않으므로, 소스 파일이 키 오름차순으로 미리 정렬되어 있어야 한다(추측).

---

## 스텝 구성

| 스텝명  | EXEC PGM | 역할 |
|---------|----------|------|
| STEP05  | `IDCAMS` | 기존 `TRANCATG.VSAM.KSDS` CLUSTER 삭제; `SET MAXCC=0`으로 "없음" 오류를 무시해 멱등성 확보 (L.25–27) |
| STEP10  | `IDCAMS` | KSDS 신규 정의: 복합키 6B 오프셋 0, 고정 레코드 60B, `SHAREOPTIONS(2 3)`, `ERASE`, 볼륨 `AWSHJ1` (L.36–49) |
| STEP15  | `IDCAMS` | `REPRO INFILE(TRANCATG) OUTFILE(TCATVSAM)`: 플랫 파일 → KSDS 적재 (L.61) |

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음. 이 잡은 PROC을 호출하지 않으며 모든 제어문이 인라인 `SYSIN`으로 공급된다.

- **호출 프로그램 (EXEC PGM)**: `IDCAMS` — IBM 제공 VSAM/카탈로그 유틸리티. 모든 스텝에서 동일하게 사용된다.

- **데이터셋/파일/DB 테이블**:

  | 역할 | DSN | 비고 |
  |------|-----|------|
  | 삭제 대상 KSDS | `AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS` | STEP05에서 조건부 삭제 |
  | 신규 정의 KSDS (DATA) | `AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS.DATA` | STEP10 정의 결과물 |
  | 신규 정의 KSDS (INDEX) | `AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS.INDEX` | STEP10 정의 결과물 |
  | 입력 플랫 파일 (PS) | `AWS.M2.CARDDEMO.TRANCATG.PS` | STEP15 REPRO 소스; `DISP=SHR` (L.57) |
  | 출력 KSDS | `AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS` | STEP15 REPRO 타겟; `DISP=OLD` (L.59) |

  레코드 레이아웃 (`CVTRA04Y`):
  ```
  01  TRAN-CAT-RECORD.                       ← 총 60바이트
      05  TRAN-CAT-KEY.
         10  TRAN-TYPE-CD   PIC X(02).       ← KSDS 복합키 선두 2바이트 (거래 유형)
         10  TRAN-CAT-CD    PIC 9(04).       ← KSDS 복합키 후행 4바이트 (카테고리 코드)
      05  TRAN-CAT-TYPE-DESC PIC X(50).      ← 카테고리 설명 50바이트
      05  FILLER             PIC X(04).      ← 패딩 4바이트
  ```

  로컬 샘플 데이터: `app/data/ASCII/trancatg.txt`, `app/data/EBCDIC/AWS.M2.CARDDEMO.TRANCATG.PS`

- **선행/후행 잡**:

  | 관계 | 잡 이름 | 설명 |
  |------|---------|------|
  | 선행 | (없음) | 독립 초기화 잡. `TRANCATG.PS`만 존재하면 된다. |
  | 유사 병렬 | `TRANTYPE` | 거래 유형 KSDS를 동일 패턴으로 구성; 두 잡은 서로 독립 |
  | 후행 (소비자) | `TRANREPT` | `CBTRN03C`가 이 KSDS를 `TRANCATG DD`로 마운트해 거래 리포트의 카테고리 설명 룩업에 사용 (TRANREPT.jcl L.71–72) |
  | 야간 배치 | `POSTTRAN` 등 | POSTTRAN(`CBTRN02C`)은 직접 사용하지 않음. 카테고리 잔액은 `TCATBALF`에 별도 관리됨. |

---

## Java/현대화 노트

### 1. IDCAMS 3-스텝 패턴 → Java/Spring 마이그레이션 대응

| COBOL/JCL 구성 | Java 현대화 등가물 |
|-----------------|-------------------|
| STEP05 DELETE + `SET MAXCC=0` | `DROP TABLE IF EXISTS tran_category` 또는 Flyway/Liquibase `DROP ... IF EXISTS` 마이그레이션 |
| STEP10 DEFINE CLUSTER | DDL `CREATE TABLE tran_category (...)` + 유니크 인덱스 정의 |
| STEP15 REPRO (PS → KSDS) | Spring Batch `FlatFileItemReader` → `JdbcBatchItemWriter` 또는 `CsvToBean` → JPA `saveAll()` |

### 2. VSAM KSDS 복합키 → RDB 복합 PK

KSDS의 `KEYS(6 0)`은 레코드 오프셋 0부터 6바이트가 키임을 의미한다. 이는 `TRAN-TYPE-CD(2B) || TRAN-CAT-CD(4B)` 복합이다.

```java
@Entity
@Table(name = "tran_category")
public class TranCategory {
    @EmbeddedId
    private TranCategoryKey id;      // TRAN-TYPE-CD + TRAN-CAT-CD 복합 PK

    @Column(length = 50)
    private String description;      // TRAN-CAT-TYPE-DESC PIC X(50)
}

@Embeddable
public class TranCategoryKey implements Serializable {
    @Column(length = 2)
    private String typeCode;         // TRAN-TYPE-CD PIC X(02) — DISPLAY, ASCII 변환 주의

    @Column(name = "cat_code")
    private Integer catCode;         // TRAN-CAT-CD PIC 9(04) — 4자리 정수
}
```

### 3. `SHAREOPTIONS(2 3)` 의미

`SHAREOPTIONS(2 3)`: 동일 시스템 내에서 복수 잡이 공유(R/O 복수 + R/W 단일 허용), 다른 시스템에서는 완전 공유. CICS와 배치가 동시에 이 파일을 열 수 있음을 시사한다. Java에서는 DB 트랜잭션 격리 수준(READ COMMITTED 이상)으로 대응.

### 4. IDCAMS REPRO 시 주의사항

- REPRO는 레코드를 키 순서로 정렬하지 않는다. 소스 PS(`TRANCATG.PS`)가 이미 `TRAN-TYPE-CD` + `TRAN-CAT-CD` 오름차순으로 정렬되어 있어야 KSDS 적재가 정상 완료된다. 미정렬이면 `REPRO` 단계에서 `DUPLICATE KEY` 오류 또는 로드 실패가 발생한다.
- `DISP=OLD`(STEP15 TCATVSAM): REPRO 중 다른 잡의 접근을 차단하는 배타 잠금. Java LOAD 단계에서는 테이블 레벨 잠금 또는 LOCK TABLE을 고려할 것.

### 5. EBCDIC → ASCII 변환

`TRAN-CAT-TYPE-DESC PIC X(50)`는 EBCDIC 문자열이다. VSAM에서 직접 읽으면 문자가 깨진다. Java 마이그레이션 시 `Charset.forName("Cp1047")` 또는 `IBM-1047` 코드 페이지로 디코딩 후 UTF-8로 저장해야 한다. 샘플 ASCII 파일(`app/data/ASCII/trancatg.txt`)은 이미 변환된 형태이므로 참조용으로 활용 가능하다.

### 6. 이 잡이 야간 배치에서 갖는 위치

거래 카테고리 마스터는 정적 참조 데이터(코드 테이블)다. 야간 배치 사이클(`POSTTRAN → INTCALC → ...`) 실행 도중에는 변경되지 않으며, `TRANCATG` 잡은 환경 초기 구성 또는 마스터 데이터 재설정 시에만 수동으로 실행된다. Java 마이그레이션에서는 이 데이터를 DB 시드 스크립트(Flyway `V__seed_tran_category.sql`) 또는 Spring Boot `data.sql`로 대체할 수 있다.
