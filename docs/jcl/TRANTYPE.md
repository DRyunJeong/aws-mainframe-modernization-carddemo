# TRANTYPE — 거래 유형 VSAM 파일 초기 적재

- **유형**: JCL
- **한 줄 요약**: 거래 유형 코드 참조 테이블(AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS)을 처음부터 새로 구축하는 VSAM 초기화·적재 잡 — 기존 클러스터 삭제 → 신규 정의 → 순차 PS 파일에서 REPRO로 복사, 3단계 구성.

---

## 기능 설명

TRANTYPE 잡은 거래 유형 마스터 데이터를 메인프레임 VSAM KSDS 파일로 올리는 **일회성 초기화(혹은 재초기화) 잡**이다.
순차 PS 파일(`AWS.M2.CARDDEMO.TRANTYPE.PS`)에 저장된 평문 레코드를 읽어 키 기반 VSAM 클러스터(`AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS`)에 적재한다.

처리 흐름은 다음 세 단계로 구성된다.

1. **멱등 삭제(STEP05)**: 클러스터가 이미 존재하면 삭제하고, 없어도 `SET MAXCC = 0`으로 리턴코드를 강제로 0으로 리셋하여 잡이 계속 진행되도록 한다. 이 패턴은 "항상 처음부터 새로 만들기"를 보장하는 VSAM 초기화의 관용구다.
2. **VSAM 클러스터 정의(STEP10)**: IDCAMS의 `DEFINE CLUSTER` 명령으로 KSDS를 생성한다. 키 길이 2바이트, 오프셋 0(거래 유형 코드 2자리), 고정 레코드 크기 60바이트, 볼륨 AWSHJ1, 초기 1실린더(최대 5실린더 확장).
3. **데이터 복사(STEP15)**: IDCAMS `REPRO` 명령이 순차 DD `TRANTYPE`(PS)를 읽어 `TTYPVSAM`(KSDS)에 모두 삽입한다.

샘플 데이터(`app/data/ASCII/trantype.txt`)에 따르면 거래 유형은 7개 레코드로 구성된다.

```
01  Purchase
02  Payment
03  Credit
04  Authorization
05  Refund
(2개 추가)
```

이 데이터는 런타임에 CBTRN03C(거래 리포트 배치)가 거래 유형 코드를 설명 문자열로 변환할 때 RANDOM 읽기(`READ TRANTYPE-FILE INTO TRAN-TYPE-RECORD`)로 참조한다.

---

## 스텝 구성

| 스텝명  | EXEC PGM/PROC | 역할 |
|---------|--------------|------|
| STEP05  | `IDCAMS`     | 기존 VSAM 클러스터 `AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS` 삭제 (없어도 오류 무시: `SET MAXCC = 0`) |
| STEP10  | `IDCAMS`     | 새 KSDS 클러스터 정의: KEY(2,0), RECORDSIZE(60,60), CYLINDERS(1,5), 볼륨 AWSHJ1 |
| STEP15  | `IDCAMS`     | `REPRO INFILE(TRANTYPE) OUTFILE(TTYPVSAM)` — 순차 PS에서 KSDS로 전체 복사 |

> 세 스텝 모두 `EXEC PGM=IDCAMS`를 사용한다. COBOL 프로그램은 전혀 호출되지 않는 순수 유틸리티 잡이다.

---

## 의존성

### COPY (PROC/INCLUDE)

- 없음. 카탈로그드 프로시저 참조 없음.

### 호출 프로그램 (EXEC PGM)

- `IDCAMS` — IBM 제공 AMS(Access Method Services) 유틸리티. DEFINE CLUSTER, DELETE CLUSTER, REPRO를 수행하는 시스템 프로그램. 별도 LOADLIB 불필요.

### 데이터셋/파일/DB 테이블

| 데이터셋 | DD명 | 속성 | 역할 |
|----------|------|------|------|
| `AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS` | SYSIN 내 명령 대상 / TTYPVSAM | VSAM KSDS, KEY(2,0), RECL=60 고정 | 생성 대상 클러스터 (출력) |
| `AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS.DATA` | — | 데이터 컴포넌트 | DEFINE CLUSTER의 DATA 서브컴포넌트 |
| `AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS.INDEX` | — | 인덱스 컴포넌트 | DEFINE CLUSTER의 INDEX 서브컴포넌트 |
| `AWS.M2.CARDDEMO.TRANTYPE.PS` | TRANTYPE (STEP15) | 순차(PS), DISP=SHR | REPRO 소스 — 미리 적재된 거래 유형 레코드 |

**KSDS 레코드 레이아웃** (copybook `app/cpy/CVTRA03Y.cpy` 기준):

```cobol
01  TRAN-TYPE-RECORD.
    05  TRAN-TYPE       PIC X(02).    ← 키 (예: "01", "02" …)
    05  TRAN-TYPE-DESC  PIC X(50).    ← 설명 (예: "Purchase", "Payment")
    05  FILLER          PIC X(08).    ← 패딩, 합계 60바이트
```

Java 대응:

```java
public class TranType {
    private String code;        // PIC X(02) — 2자리 문자열
    private String description; // PIC X(50) — 고정 50바이트 → String.format("%-50s", ...)
    // FILLER 8바이트는 Java 객체에서 불필요
}
```

### 선행/후행 잡

- **선행 잡**: 없음. `AWS.M2.CARDDEMO.TRANTYPE.PS` 순차 파일이 사전에 존재해야 하나, 이를 생성하는 잡이 별도로 정의되어 있지 않다. `app/data/EBCDIC/AWS.M2.CARDDEMO.TRANTYPE.PS` 파일을 FTP 등으로 올려두어야 한다(추측). 야간 배치 시퀀스(POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANIDX → OPENFIL)의 공식 일부가 아닌 **초기 설치 시 1회성 잡**이다.
- **후행 잡(소비자)**: `TRANREPT.jcl` — CBTRN03C를 구동하는 거래 리포트 잡이 STEP10R에서 `//TRANTYPE DD DSN=AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS`로 이 파일을 RANDOM 읽기 참조한다. TRANTYPE 잡이 실행되어 있지 않으면 TRANREPT 잡의 CBTRN03C가 OPEN 실패로 abend된다.

---

## Java/현대화 노트

### 1. 멱등 DELETE + `SET MAXCC = 0` 패턴

STEP05의 구조는 "선삭제 후생성"으로 **멱등성(idempotency)을 보장**하는 표준 VSAM 관용구다. Java/Spring에서의 대응:

```java
// Flyway 또는 Liquibase 마이그레이션 예시
// DROP TABLE IF EXISTS tran_type; -- 동일 개념
// CREATE TABLE tran_type (...);
```

`SET MAXCC = 0`은 JCL 제어 흐름에서 "이전 스텝 실패를 무시하고 계속"을 의미한다. CI/CD 파이프라인에서 `|| true` 또는 `set +e`와 개념적으로 동일하다.

### 2. IDCAMS REPRO = 벌크 삽입

`REPRO INFILE(TRANTYPE) OUTFILE(TTYPVSAM)`은 소스 파일의 모든 레코드를 KSDS에 순차 삽입한다. Java 대응:

```java
// Spring Batch FlatFileItemReader + JdbcBatchItemWriter 조합
// 또는 단순히:
List<TranType> records = readFromFlatFile("trantype.txt");
tranTypeRepository.saveAll(records);  // JPA 배치 삽입
```

### 3. VSAM KSDS → 관계형 DB 테이블 매핑

| VSAM 개념 | 관계형/Java 대응 |
|-----------|----------------|
| KSDS (Keyed Sequential Data Set) | PRIMARY KEY가 있는 테이블 |
| `KEYS(2 0)` — 키 길이 2, 오프셋 0 | `CHAR(2) PRIMARY KEY` |
| `RECORDSIZE(60 60)` — 고정 60바이트 | 각 컬럼 합계 = 60 (`CHAR(2)+VARCHAR(50)+CHAR(8)`) |
| `SHAREOPTIONS(1 4)` | 레벨 1: 동일 시스템 내 다중 리더 허용, 레벨 4: 다중 시스템 전체 공유 허용 |
| `ERASE` | 삭제 시 물리적 0으로 덮어씀 (보안 삭제) |
| `CYLINDERS(1 5)` | 초기 1실린더 할당, 최대 5실린더 확장 (약 700~900 KB/실린더, 추측) |

DDL 예시:
```sql
CREATE TABLE tran_type (
    tran_type_cd   CHAR(2)      NOT NULL PRIMARY KEY,  -- PIC X(02), 오프셋 0
    tran_type_desc VARCHAR(50)  NOT NULL,              -- PIC X(50)
    -- FILLER 8바이트는 불필요
    CONSTRAINT chk_code CHECK (tran_type_cd ~ '^[0-9]{2}$')
);
```

### 4. 레코드 크기 주의

VSAM KSDS 정의의 `RECORDSIZE(60 60)`은 최소=최대=60바이트 고정 레코드임을 의미한다. copybook `CVTRA03Y.cpy`에서도 합산하면 `2 + 50 + 8 = 60`바이트로 일치한다. 순차 PS 소스 파일(`TRANTYPE.PS`)도 동일 60바이트 고정 레코드여야 REPRO가 정상 동작한다. ASCII 샘플 파일(`app/data/ASCII/trantype.txt`)에서 각 줄이 60자인지 확인 필요 — EBCDIC 환경에서는 코드 페이지 변환 없이 바이너리 그대로 FTP해야 한다(`FTP binary`, NOT `ascii` 모드).

### 5. 소비자 프로그램 CBTRN03C와의 연결

CBTRN03C는 TRANTYPE 파일을 `SELECT TRANTYPE-FILE ASSIGN TO TRANTYPE` / `ACCESS MODE IS RANDOM`으로 선언하고, 거래 레코드의 `TRAN-TYPE-CD` 2자리로 직접 READ한다(`READ TRANTYPE-FILE INTO TRAN-TYPE-RECORD KEY IS FD-TRAN-TYPE`). Java 마이그레이션 시 이 RANDOM READ는 `tranTypeRepository.findById(typeCode)` 또는 애플리케이션 시작 시 `Map<String, TranType>`으로 전체 캐싱하는 방식으로 대체할 수 있다. 거래 유형은 7건으로 작아 캐싱이 적합하다.

---

*소스 파일: `app/jcl/TRANTYPE.jcl` | 버전: CardDemo_v1.0-15-g27d6c6f-68 (2022-07-19)*
