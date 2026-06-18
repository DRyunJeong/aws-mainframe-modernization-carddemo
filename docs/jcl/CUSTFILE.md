# CUSTFILE — 고객 마스터 VSAM 파일 정의·적재 잡

- **유형**: JCL
- **한 줄 요약**: 고객 마스터 VSAM KSDS(`CUSTDATA`)를 완전 재생성하고, 평문 순차 파일에서 레코드를 일괄 적재(REPRO)하는 배치 잡. 작업 전·후 CICS 파일을 닫고 열어 서비스 무중단을 유지한다.

---

## 기능 설명

이 잡은 다음 다섯 가지 작업을 순서대로 수행하며, 고객 마스터 VSAM 파일을 처음부터 완전히 재구성(drop-and-reload)한다.

1. **CICS 파일 닫기** — CICS 리전이 `CUSTDAT` 파일을 열고 있으면 배치가 VSAM에 독점 접근할 수 없다. CICS에 `CEMT SET FIL(CUSTDAT) CLO` 명령을 보내 먼저 닫는다.
2. **기존 VSAM 클러스터 삭제** — `IDCAMS DELETE`로 기존 KSDS를 제거한다. 파일이 없어도 조건부(`IF MAXCC LE 08 THEN SET MAXCC = 0`)로 오류를 억제하므로 최초 실행(파일 미존재) 시에도 정상 진행된다.
3. **신규 VSAM 클러스터 정의** — `IDCAMS DEFINE CLUSTER`로 KSDS를 새로 생성한다. 키 길이 9바이트(오프셋 0), 레코드 최소/최대 500바이트, 볼륨 `AWSHJ1`, 1차 1실린더/2차 5실린더 할당.
4. **평문 PS → VSAM 복사** — `IDCAMS REPRO`로 순차 파일(`CUSTDATA.PS`)의 레코드를 KSDS(`CUSTDATA.VSAM.KSDS`)에 키 순서대로 삽입한다.
5. **CICS 파일 열기** — 로드 완료 후 `CEMT SET FIL(CUSTDAT) OPE`로 CICS에 파일을 다시 열어 온라인 거래를 재개한다.

---

## 스텝 구성

| 스텝명    | EXEC PGM   | 역할 |
|-----------|------------|------|
| `CLCIFIL` | `SDSF`     | SDSF를 통해 CICS 리전(`CICSAWSA`)에 MODIFY 명령 전달 → `CUSTDAT` 파일 닫기 (라인 22–27) |
| `STEP05`  | `IDCAMS`   | 기존 `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` 클러스터 삭제, MAXCC≤8이면 정상(not-found RC=8 허용) (라인 32–38) |
| `STEP10`  | `IDCAMS`   | KSDS 신규 정의: KEYS(9 0), RECORDSIZE(500 500), SHAREOPTIONS(2 3), ERASE 옵션 (라인 43–59) |
| `STEP15`  | `IDCAMS`   | `CUSTDATA`(PS) → `CUSTVSAM`(KSDS) REPRO(bulk load) (라인 64–71) |
| `OPCIFIL` | `SDSF`     | CICS 리전에 MODIFY 명령 전달 → `CUSTDAT` 파일 열기 (라인 76–81) |

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 — 모든 스텝이 인라인 `SYSIN DD *` 사용, 외부 PROC 참조 없음.

- **호출 프로그램 (EXEC PGM)**:
  - `SDSF` — IBM SDSF 유틸리티. ISFIN DD의 `/F` 명령어로 CICS 리전에 `CEMT` 메시지를 보내는 간접 방식. SDSF 방식 대신 `CEMTUTL` 또는 `CEDF`를 사용하는 사이트도 있다(사이트별 차이).
  - `IDCAMS` — IBM Access Method Services. DELETE/DEFINE CLUSTER/REPRO 세 가지 기능을 담당하는 VSAM 관리 유틸리티.

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.CUSTDATA.PS` — 소스 평문 순차파일(RECFM=FB, LRECL=500 추측). 레코드 레이아웃은 copybook `CVCUS01Y`(`CUSTOMER-RECORD`, 500바이트) 참조.
  - `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` — 타깃 VSAM KSDS. 키: 오프셋 0, 길이 9(고객 ID = `CUST-ID PIC 9(9)` 추측).
  - `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS.DATA` — KSDS 데이터 컴포넌트.
  - `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS.INDEX` — KSDS 인덱스 컴포넌트.
  - 볼륨 `AWSHJ1` — VSAM 클러스터가 할당되는 디스크 볼륨(환경별 상이).

- **선행/후행 잡**:
  - 선행: 없음(최초 기동 가능). 단, `CUSTDATA.PS` 원천 파일이 사전에 존재해야 한다(데이터 준비 잡 또는 export 잡 선행 권장).
  - 후행: 야간 배치 시퀀스(`run_full_batch.sh`)에서 `CUSTFILE`은 마스터 갱신 단계 중 하나다. 이후 `POSTTRAN`(CBTRN02C) → `INTCALC`(CBACT04C) 순으로 연결된다. 온라인 CICS 프로그램 중 고객 정보를 조회하는 `COACTUPC`·`COBIL00C` 등이 이 VSAM을 사용한다.

---

## Java / 현대화 노트

### 1. drop-and-reload 패턴 → truncate + batch insert
COBOL/JCL의 "DELETE 클러스터 → DEFINE → REPRO" 3단계는 Java에서 "테이블 TRUNCATE(또는 DELETE ALL) → JDBC batch insert / Spring Batch `JdbcBatchItemWriter`"에 해당한다. VSAM KSDS는 관계형 DB의 기본 키 인덱스 테이블과 동일한 구조다.

```java
// Spring Batch 대응 예시
@Bean
public Step loadCustomerStep(JobRepository jr, PlatformTransactionManager tm,
        FlatFileItemReader<CustomerRecord> reader,
        JdbcBatchItemWriter<CustomerRecord> writer) {
    return new StepBuilder("loadCustomerStep", jr)
            .<CustomerRecord, CustomerRecord>chunk(1000, tm)
            .reader(reader)   // PS(LRECL=500 고정길이) → FixedLengthTokenizer
            .writer(writer)   // INSERT INTO CUSTDATA ...
            .build();
}
```

### 2. KSDS 키(KEYS(9 0)) → 기본 키 매핑
`KEYS(9 0)`는 레코드 오프셋 0부터 9바이트가 키임을 의미한다. `CVCUS01Y` copybook의 `CUST-ID PIC 9(9)` 필드(DISPLAY, 9자리 숫자 문자열)가 이 키에 대응한다. Java에서는 `String` 또는 9자리를 꽉 채운 `Long`으로 표현하되, EBCDIC→ASCII 변환 시 선행 0이 유지되는지 확인해야 한다.

### 3. SHAREOPTIONS(2 3)의 의미
SHAREOPTIONS(cross-region=2, cross-system=3)는 동일 LPAR 내 여러 address space가 동시에 파일을 열 수 있되, 갱신 무결성은 애플리케이션이 보장해야 함을 뜻한다(Java의 낙관적 락과 유사). 그래서 배치 전에 CICS 파일을 반드시 닫는 것이다. Java 마이그레이션 시 이 부분은 DB 트랜잭션 격리 수준과 배치 잠금 전략으로 대체된다.

### 4. CICS CEMT SET FIL 처리 → 서비스 중단(quiesce) API
SDSF를 통한 CEMT 명령은 메인프레임에서만 동작하는 운영 제어 방식이다. AWS Mainframe Modernization(AWS M2)이나 Micro Focus 환경으로 마이그레이션하면 동등한 "파일 닫기/열기"가 필요 없어진다(DB connection pool이 자동으로 처리). 단, 배치 재로드 시 온라인 조회 중단이 필요한 요구사항이라면 별도의 서킷 브레이커 또는 maintenance mode API로 구현해야 한다.

### 5. IF MAXCC LE 08 패턴 → 멱등성 보장
`IF MAXCC LE 08 THEN SET MAXCC = 0`은 "파일이 없어도 오류로 처리하지 않는다"는 멱등(idempotent) 실행 보장 코드다. Java/Spring Batch에서는 `@BeforeStep`에서 테이블 존재 여부를 확인하거나, `CREATE TABLE IF NOT EXISTS` / `DELETE FROM ... WHERE 1=1`로 동일한 효과를 낸다.

### 6. RECORDSIZE(500 500) → 고정 길이 레코드
최소=최대=500바이트의 고정 레코드다. Java로 읽을 때 `FixedLengthLineTokenizer` 또는 바이트 배열 직접 파싱이 필요하며, EBCDIC 인코딩(`Cp1047` 또는 `IBM01047`) 변환을 명시해야 한다.

```java
// 고정 길이 500바이트 파일 읽기
FixedLengthLineTokenizer tokenizer = new FixedLengthLineTokenizer();
tokenizer.setColumns(new Range[]{
    new Range(1, 9),   // CUST-ID
    new Range(10, 35), // CUST-FIRST-NAME
    // ... CVCUS01Y 레이아웃 순서대로
});
```

---

*버전: CardDemo_v1.0-15-g27d6c6f-68 / 2022-07-19*
