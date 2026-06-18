# COMBTRAN — 거래 파일 병합·정렬 및 VSAM 마스터 반영

- **유형**: JCL (IBM Utility 잡 — SORT + IDCAMS)
- **한 줄 요약**: 백업된 일일 거래(`TRANSACT.BKUP`)와 시스템 생성 거래(`SYSTRAN`)를 `TRAN-ID` 오름차순으로 병합 정렬하여 GDG 통합 파일(`TRANSACT.COMBINED`)을 생성한 뒤, IDCAMS REPRO로 VSAM KSDS 마스터(`TRANSACT.VSAM.KSDS`)에 적재한다.

---

## 기능 설명

야간 배치 시퀀스에서 POSTTRAN(CBTRN02C) → INTCALC(CBACT04C) → TRANBKP 단계가 끝난 뒤 실행된다.
이 시점에 두 종류의 거래 레코드가 별도 데이터셋에 분리되어 있다.

| 소스 | 내용 |
|---|---|
| `TRANSACT.BKUP(0)` | TRANBKP 잡이 생성한 당일 일일 거래 백업(GDG 현재 세대) |
| `SYSTRAN(0)` | INTCALC(CBACT04C)가 계정별로 생성한 시스템 이자 거래(GDG 현재 세대) |

COMBTRAN은 두 스트림을 단일 SORT 패스로 병합 정렬하고, 결과를 GDG 다음 세대(`+1`)로 카탈로그한 다음, 해당 통합 파일을 VSAM KSDS에 복사함으로써 CICS 온라인 프로그램이 조회할 수 있는 최신 거래 마스터를 완성한다.

Java/Spring Batch 관점으로 보면 이 잡은 두 개의 `FlatFileItemReader` 스트림을 `TRAN-ID` 기준으로 정렬·병합(Merge Sort)하는 단계와, 정렬 결과를 VSAM 상당 저장소(예: KSDS → RocksDB/NoSQL/RDBMS의 PK 인덱스 테이블)에 벌크 인서트하는 단계가 순차 실행되는 청크 잡에 해당한다.

---

## 스텝 구성

| 스텝명 | EXEC PGM / UTILITY | 역할 |
|---|---|---|
| `STEP05R` | `PGM=SORT` (DFSORT 또는 SYNCSORT) | `TRANSACT.BKUP(0)`과 `SYSTRAN(0)`을 다중 SORTIN으로 읽어 `TRAN-ID`(오프셋 1, 길이 16, 문자형) 오름차순 정렬 후 `TRANSACT.COMBINED(+1)` GDG로 출력. DCB는 SORTIN에서 복사(`DCB=(*.SORTIN)`). |
| `STEP10` | `PGM=IDCAMS` | STEP05R에서 생성된 `TRANSACT.COMBINED(+1)`을 `REPRO` 명령으로 VSAM KSDS(`TRANSACT.VSAM.KSDS`)에 복사(기존 레코드가 있으면 KEY 충돌 → IDCAMS는 SYSPRINT에 오류를 기록하고 RC=8 반환). |

### STEP05R 상세

```jcl
//STEP05R  EXEC PGM=SORT
//SORTIN   DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.TRANSACT.BKUP(0)          ← 일일 거래 백업
//         DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.SYSTRAN(0)                 ← 시스템(이자) 거래
//SYMNAMES DD *
TRAN-ID,1,16,CH                                           ← 정렬 키 이름 정의
//SYSIN    DD *
 SORT FIELDS=(TRAN-ID,A)                                  ← TRAN-ID 오름차순
//SORTOUT  DD DISP=(NEW,CATLG,DELETE),
//         UNIT=SYSDA,
//         DCB=(*.SORTIN),                                ← 입력과 동일 레코드 포맷
//         SPACE=(CYL,(1,1),RLSE),
//         DSN=AWS.M2.CARDDEMO.TRANSACT.COMBINED(+1)      ← GDG 신규 세대 생성
```

- `SYMNAMES` DD: `TRAN-ID` 심볼 이름에 오프셋 1, 길이 16, 타입 CH(Character=DISPLAY)를 바인딩. Java의 `Comparator<Transaction> comparingByTranId` 에 해당.
- 다중 SORTIN DD: SORT 유틸리티가 두 DD를 논리적으로 연결(concatenation)하여 단일 입력 스트림으로 처리. Java의 `SequenceInputStream` 또는 `Iterables.concat()` 에 해당.
- `DCB=(*.SORTIN)`: 출력 DCB(레코드 형식, 길이 등)를 첫 번째 SORTIN DD에서 상속. 레코드 포맷 하드코딩 없이 입력과 동일 구조를 보장.
- GDG `(+1)`: 현재 세대(`0`)를 보존하면서 신규 세대를 카탈로그. DISP의 세 번째 파라미터 `DELETE`는 잡 비정상 종료 시 해당 세대를 삭제하는 롤백 정책.

### STEP10 상세

```jcl
//STEP10 EXEC PGM=IDCAMS
//TRANSACT DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.TRANSACT.COMBINED(+1)      ← STEP05R 출력을 입력으로
//TRANVSAM DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS         ← 대상 VSAM KSDS
//SYSIN    DD *
   REPRO INFILE(TRANSACT) OUTFILE(TRANVSAM)
```

- `REPRO`: 순차(또는 GDG) 파일을 VSAM KSDS에 레코드 단위로 복사. KSDS의 키 중복 시 RC=8(논리 오류)이지만 CONDITION CODE 미체크 시 후속 잡이 비정상 데이터를 읽을 수 있음(주의 항목).
- CICS의 `TRANSACT.VSAM.KSDS`는 OPENFIL 잡이 재오픈하기 전까지 CICS에 의해 닫혀 있어야 REPRO가 정상 수행됨. CLOSEFIL→배치 시퀀스→OPENFIL 순서가 강제되는 이유.

---

## 의존성

### COPY (PROC/INCLUDE)

이 JCL은 외부 PROC나 INCLUDE를 참조하지 않는다. 모든 DD와 유틸리티 제어문이 인라인으로 정의되어 있다.

### 호출 프로그램 (EXEC PGM)

| 프로그램 | 출처 | 역할 |
|---|---|---|
| `SORT` | IBM DFSORT (또는 CA SYNCSORT) — 시스템 유틸리티 | 다중 입력 병합 정렬 |
| `IDCAMS` | IBM Access Method Services — 시스템 유틸리티 | VSAM 관리 및 REPRO 복사 |

별도 COBOL 프로그램을 호출하지 않는다. 유틸리티 전용 잡.

### 데이터셋/파일/DB 테이블

| DSN | 유형 | 방향 | 설명 |
|---|---|---|---|
| `AWS.M2.CARDDEMO.TRANSACT.BKUP(0)` | GDG 세대(순차) | INPUT | TRANBKP 잡이 생성한 당일 일일 거래 백업 |
| `AWS.M2.CARDDEMO.SYSTRAN(0)` | GDG 세대(순차) | INPUT | INTCALC(CBACT04C)가 생성한 시스템 이자 거래 |
| `AWS.M2.CARDDEMO.TRANSACT.COMBINED(+1)` | GDG 세대(순차) | OUTPUT | STEP05R 정렬 출력 / STEP10 REPRO 입력 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | VSAM KSDS | OUTPUT | 거래 마스터 파일 — CICS 온라인 조회 대상 |

- `TRANSACT.VSAM.KSDS`의 레코드 레이아웃은 `app/cpy/CVTRA05Y.cpy` copybook에 정의되어 있다(추측: CVTRA05Y는 거래 마스터 레이아웃 copybook — 정확한 매핑은 copybook_record_layouts 메모리 참조).
- `TRAN-ID`는 오프셋 1, 길이 16, DISPLAY 타입으로 KSDS의 논리 키이자 SORT 기준 필드.

### 선행/후행 잡

```
[야간 배치 시퀀스]

CLOSEFIL          ← CICS에서 VSAM 파일 닫기
    ↓
POSTTRAN          ← CBTRN02C: 일일 거래 검증·TRANSACT KSDS 기록
    ↓
INTCALC           ← CBACT04C: 이자 계산 → SYSTRAN(이자 거래) 생성
    ↓
TRANBKP           ← TRANSACT.VSAM.KSDS 내용을 TRANSACT.BKUP(+1)으로 백업
    ↓
COMBTRAN (본 잡)  ← BKUP(0) + SYSTRAN(0) 병합 정렬 → COMBINED(+1) → KSDS 적재
    ↓
TRANIDX           ← TRANSACT AIX(대체 인덱스) 재구성
    ↓
OPENFIL           ← CICS에서 VSAM 파일 재오픈
```

선행 잡 `TRANBKP`와 `INTCALC`가 각각 `TRANSACT.BKUP`와 `SYSTRAN`의 현재 세대(`0`)를 생성한 뒤 COMBTRAN이 실행되어야 한다. TRANIDX는 COMBTRAN이 완성한 KSDS를 기반으로 AIX를 재정의하므로 후행 의존성이 있다.

---

## Java/현대화 노트

### 1. GDG(Generation Data Group) → 버전 관리 스토리지

COBOL의 GDG는 `(0)` = 현재 세대, `(+1)` = 신규 세대, `(-1)` = 이전 세대로 파일 이력을 관리한다.

```java
// Java 현대화 대응: 파일 경로에 날짜/시퀀스를 포함하여 세대 관리
String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
Path combinedPath = Path.of("/data/transactions/COMBINED_" + today + ".dat");
// 또는 S3: s3://carddemo/transactions/combined/2024/01/15/combined.dat
```

### 2. 다중 SORTIN 병합 → Java Stream 병합

```jcl
//SORTIN   DD DISP=SHR,DSN=...TRANSACT.BKUP(0)
//         DD DISP=SHR,DSN=...SYSTRAN(0)
 SORT FIELDS=(TRAN-ID,A)
```

```java
// Java 대응: 두 소스를 정렬 병합
List<Transaction> dailyTrans = readFromBkup();
List<Transaction> sysTrans   = readFromSystran();

List<Transaction> combined = Stream.concat(dailyTrans.stream(), sysTrans.stream())
    .sorted(Comparator.comparing(Transaction::getTranId))
    .collect(Collectors.toList());
```

Spring Batch에서는 `MultiResourceItemReader` 또는 `CompositeItemReader`로 두 파일을 읽고, `SortItemWriter` 패턴이나 DB의 `ORDER BY`로 정렬을 처리한다.

### 3. IDCAMS REPRO → UPSERT 주의사항

`REPRO`는 KSDS에 레코드를 순차 INSERT한다. 키 중복 레코드가 있으면 RC=8을 반환하고 해당 레코드를 건너뛴다. Java로 마이그레이션 시 두 가지 선택이 있다.

```java
// 옵션 A: INSERT 실패 허용 — UPSERT(INSERT OR REPLACE) 사용
repository.saveAll(combinedTransactions);  // JPA saveAll은 UPSERT

// 옵션 B: 테이블 TRUNCATE 후 전체 재적재 (REPRO의 의도에 더 가까움)
transactionRepository.deleteAll();
transactionRepository.saveAll(combinedTransactions);
```

주의: REPRO는 REPLACE 옵션 없이는 기존 KSDS 레코드를 덮어쓰지 않는다. 이자 거래가 POSTTRAN에서 이미 KSDS에 기록되었다면 STEP10 실행 시 키 중복 RC=8이 발생할 수 있다(라인 48 `REPRO INFILE(TRANSACT) OUTFILE(TRANVSAM)`에 REPLACE 키워드 없음). 현대화 시 멱등성(idempotency) 설계가 필요하다.

### 4. DCB 상속(`DCB=(*.SORTIN)`) → 스키마 계약

`DCB=(*.SORTIN)`은 출력 파일의 레코드 포맷(RECFM), 레코드 길이(LRECL), 블록 크기(BLKSIZE)를 첫 번째 SORTIN에서 복사하는 메커니즘이다. Java에서는 DTO/레코드 클래스가 이 역할을 한다.

```java
// COBOL: DCB=(*.SORTIN) — 입력과 동일한 바이트 레이아웃을 출력에 강제
// Java: 동일 DTO 클래스가 읽기·쓰기에 모두 사용되면 동일 효과
record TransactionRecord(
    String tranId,      // offset 1, len 16 (TRAN-ID)
    // ... 나머지 필드
) {}
```

### 5. CICS VSAM 파일 잠금 — 배치 실행 전 CLOSEFIL 필수

`TRANSACT.VSAM.KSDS`는 CICS 리전이 공유 모드(`DISP=SHR`)로 열고 있는 파일이다. CLOSEFIL 잡 없이 IDCAMS REPRO를 실행하면 VSAM 충돌(ENQ 경합)로 잡이 실패한다. Java/현대 환경에서는 배치 실행 시간대에 API 서비스를 `ReadOnly` 모드로 전환하거나 CQRS 패턴으로 쓰기 스토리지와 읽기 스토리지를 분리하는 것이 권장된다.

### 6. TRAN-ID 필드 타입 확인 권고

SYMNAMES에 정의된 `TRAN-ID,1,16,CH`는 오프셋 1부터 16바이트 DISPLAY 문자열임을 선언한다. 실제 copybook(CVTRA05Y 추정)의 `PIC X(16)` 또는 `PIC 9(16)` 여부를 확인해야 한다. `PIC X(16)`이면 Java `String`으로 직접 매핑되지만, `PIC 9(16)` DISPLAY라면 선행 0 패딩이 있는 숫자 문자열이므로 `String.format("%016d", id)` 형태의 정규화가 필요하다(추측: CBTRN02C의 TRAN-ID 생성 로직이 날짜+시퀀스 조합이므로 PIC X(16) 가능성 높음).
