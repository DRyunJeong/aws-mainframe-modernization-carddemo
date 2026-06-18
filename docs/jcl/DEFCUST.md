# DEFCUST — 고객 VSAM 파일 정의 잡

- **유형**: JCL
- **한 줄 요약**: 고객 마스터 VSAM KSDS 클러스터(`AWS.CUSTDATA.CLUSTER`)를 (재)생성한다 — 기존 데이터셋이 있으면 먼저 삭제한 뒤 새로 DEFINE CLUSTER한다.

---

## 기능 설명

DEFCUST는 CardDemo의 고객 마스터 파일을 메인프레임 VSAM KSDS(Key-Sequenced Data Set)로 정의하는 환경 설정 잡이다.
실제 애플리케이션 로직은 없으며, 순수하게 **인프라 프로비저닝** 역할만 수행한다.

처리 흐름은 두 단계로 나뉜다.

1. **사전 정리**: `AWS.CCDA.CUSTDATA.CLUSTER`라는 이름의 기존 VSAM 클러스터를 IDCAMS DELETE 명령으로 삭제한다.
   클러스터가 없는 경우 IDCAMS는 RC=8(not found)을 반환하지만, JCL에 별도 COND 지정이 없으므로 다음 스텝으로 진행된다(추측 — JCL에 COND 파라미터 없음).
2. **재정의**: `AWS.CUSTDATA.CLUSTER`라는 이름의 신규 KSDS 클러스터를 IDCAMS DEFINE CLUSTER 명령으로 생성한다.

> **주의**: 두 스텝 모두 스텝명이 `STEP05`로 중복 선언되어 있다(소스 1행 `//STEP05` 및 32행 `//STEP05`).
> 표준 JCL에서 동일 잡 내 스텝명 중복은 JCLERROR를 유발하므로 실제 제출 전 두 번째 스텝명을 `STEP10` 등으로 수정해야 한다(소스 버그).

---

## 스텝 구성

| 스텝명   | EXEC PGM/PROC | 역할                                                                 |
|----------|---------------|----------------------------------------------------------------------|
| `STEP05` (1번째, 소스 22행) | `PGM=IDCAMS` | 기존 `AWS.CCDA.CUSTDATA.CLUSTER` VSAM 클러스터 DELETE |
| `STEP05` (2번째, 소스 32행) | `PGM=IDCAMS` | 신규 `AWS.CUSTDATA.CLUSTER` KSDS 클러스터 DEFINE CLUSTER |

**DEFINE CLUSTER 파라미터 상세**

| 파라미터        | 값          | 의미                                                                      |
|-----------------|-------------|---------------------------------------------------------------------------|
| `NAME`          | `AWS.CUSTDATA.CLUSTER` | 클러스터 HLQ(High-Level Qualifier) 포함 데이터셋 이름         |
| `CYLINDERS(1 5)` | 기본 1실린더, 보조 5실린더 | 초기 할당 1 CYL, 공간 부족 시 5 CYL씩 자동 확장         |
| `KEYS(10 0)`    | 길이 10바이트, 오프셋 0 | 레코드 첫 10바이트가 기본 키(Primary Key)                  |
| `RECORDSIZE(500 500)` | 평균 500바이트, 최대 500바이트 | 고정 길이 레코드(Fixed-length record) — `CVCUS01Y` copybook의 `CUSTOMER-RECORD`가 500바이트인 것과 일치 |
| `SHAREOPTIONS(1 4)` | 리전간 공유(Cross-region share option 1), 시스템간 공유(Cross-system share option 4) | 배치와 CICS가 동시에 파일을 열기 위한 공유 설정 |
| `ERASE`         | —           | 클러스터 삭제 시 데이터 영역을 0x00으로 덮어써 보안 삭제                  |
| `INDEXED`       | —           | KSDS(Key-Sequenced Data Set) 유형 명시                                    |
| `DATA NAME`     | `AWS.CUSTDATA.CLUSTER.DATA`  | 데이터 컴포넌트 이름                                       |
| `INDEX NAME`    | `AWS.CUSTDATA.CLUSTER.INDEX` | 인덱스 컴포넌트 이름                                       |

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 — 인라인 SYSIN만 사용, 카탈로그드 프로시저 참조 없음

- **호출 프로그램 (EXEC PGM)**: `IDCAMS` — IBM 제공 AMS(Access Method Services) 유틸리티. VSAM 데이터셋 정의·삭제·복사 등을 처리하는 표준 유틸. 별도 설치 불필요(z/OS 기본 제공).

- **데이터셋/파일/DB 테이블**:
  - `AWS.CCDA.CUSTDATA.CLUSTER` — 삭제 대상 기존 클러스터(존재하지 않을 수 있음)
  - `AWS.CUSTDATA.CLUSTER` — 생성 대상 신규 KSDS 클러스터(데이터 컴포넌트 `AWS.CUSTDATA.CLUSTER.DATA`, 인덱스 컴포넌트 `AWS.CUSTDATA.CLUSTER.INDEX`)
  - 레코드 레이아웃: `app/cpy/CVCUS01Y.cpy` — `CUSTOMER-RECORD`, 500바이트 고정 길이, 키는 `CUST-ID`(오프셋 0, 10바이트)

- **선행/후행 잡**:
  - 선행: 없음(최초 환경 구성 시 단독 실행 가능). 단, 파일이 이미 CICS에 의해 열려 있는 경우 DELETE가 실패하므로 CICS 리전 중지 또는 CEMT SET FILE CLOSED 선행 필요.
  - 후행: `LOADCUST`(고객 마스터 초기 데이터 적재) 잡이 이 파일을 사용한다(추측 — CardDemo `app/jcl/` 내 LOAD* JCL 패턴 기준).
  - 배치 야간 흐름에서는 `DEFACCTS`·`DEFCARD`·`DEFXREF` 등 다른 DEF* 잡들과 함께 전체 VSAM 파일 재정의 시 일괄 실행된다(추측 — `app/jcl/` 내 DEF 잡 군집 패턴 기준).

---

## Java/현대화 노트

### 1. VSAM KSDS → 관계형 DB 테이블 또는 JPA 엔티티

VSAM KSDS는 기본 키(Primary Key)로 정렬된 파일 저장소다.
Java 마이그레이션 시 다음과 같이 대응한다.

```java
// VSAM KSDS의 RECORDSIZE(500 500), KEYS(10 0) 대응
@Entity
@Table(name = "CUSTOMER")
public class CustomerRecord {
    @Id
    @Column(name = "CUST_ID", length = 10)
    private String custId;          // KEYS(10 0) — 오프셋 0, 길이 10의 Primary Key

    // ... CVCUS01Y copybook의 나머지 필드
}
```

### 2. IDCAMS DELETE + DEFINE CLUSTER → DDL DROP TABLE + CREATE TABLE

메인프레임에서 VSAM 파일 재생성은 "DELETE → DEFINE"의 두 단계다. Java 환경에서는 DDL 또는 마이그레이션 도구로 표현된다.

```sql
-- STEP05 (1번째): DELETE AWS.CCDA.CUSTDATA.CLUSTER 대응
DROP TABLE IF EXISTS CUSTOMER;

-- STEP05 (2번째): DEFINE CLUSTER ... RECORDSIZE(500 500) KEYS(10 0) 대응
CREATE TABLE CUSTOMER (
    CUST_ID        CHAR(10)     NOT NULL,
    -- ... 나머지 CVCUS01Y 필드
    PRIMARY KEY (CUST_ID)
);
```

Flyway/Liquibase 같은 마이그레이션 도구를 사용하면 이 잡의 역할을 버전 관리되는 마이그레이션 스크립트(`V1__create_customer.sql`)로 대체할 수 있다.

### 3. `CYLINDERS(1 5)` → 스토리지 프로비저닝 불필요

VSAM은 디스크 실린더 단위로 사전 할당(pre-allocation)을 요구한다. RDBS나 클라우드 스토리지는 자동 확장(auto-grow)하므로 이 파라미터는 마이그레이션 시 제거된다.

### 4. `SHAREOPTIONS(1 4)` → 커넥션 풀 / 트랜잭션 격리

SHAREOPTIONS는 CICS(온라인)와 배치 잡이 동시에 같은 파일을 열 수 있도록 하는 VSAM 수준의 공유 제어다. 관계형 DB에서는 커넥션 풀과 트랜잭션 격리 수준(Isolation Level)으로 대체된다.

### 5. 스텝명 중복 버그

소스 22행과 32행 모두 `//STEP05`로 선언되어 있다(소스 라인 직접 확인). 실제 메인프레임 제출 시 JES는 JCLERROR로 잡을 중단시킨다. 수정 전 두 번째 스텝을 `//STEP10`으로 변경해야 한다.

### 6. DELETE 대상 클러스터명 불일치

- 삭제 대상: `AWS.CCDA.CUSTDATA.CLUSTER` (소스 25행)
- 생성 대상: `AWS.CUSTDATA.CLUSTER` (소스 35행)

두 이름의 HLQ 구조가 다르다(`AWS.CCDA.*` vs `AWS.*`). DELETE가 성공해도 실제로 사용할 클러스터(`AWS.CUSTDATA.CLUSTER`)는 삭제되지 않는다. 이는 소스 내 오류이거나 환경별 HLQ 차이를 반영한 것일 수 있다(추측). 마이그레이션 시 두 이름의 일관성을 확인해야 한다.

### 7. `ERASE` 파라미터 → 보안 삭제

`ERASE` 지정 시 VSAM 클러스터 삭제 시점에 데이터 영역을 0으로 덮어쓴다. 고객 개인정보(PII)를 보호하기 위한 설정이다. Java 환경에서는 DB 테이블 DROP 전 `DELETE FROM CUSTOMER`로 데이터를 먼저 지우거나, 암호화 + 키 파기(Crypto-shredding) 방식을 고려한다.
