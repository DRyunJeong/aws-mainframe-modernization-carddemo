# cobol-explainer memory

- [Copybook record layouts](copybook_record_layouts.md) — VSAM 파일↔app/cpy copybook↔01레벨명 매핑, symbolic-map(...I/...O, L/F/I/O 필드) 컨벤션
- [Online pseudo-conversational pattern](online_pseudoconv_pattern.md) — 모든 CO* 프로그램의 공통 CICS 골격(EIBCALEN/REENTER 분기, RETURN+COMMAREA, XCTL 내비게이션, 메뉴 데이터 주도 라우팅)
- [Batch & optional modules](batch_and_optional_modules.md) — CB* 배치 I/O 골격, CBTRN02C 입력 흐름, CBACT04C 이자계산(control-break+COMPUTE), 야간 배치 시퀀스, 옵션 3모듈이 CSD·메뉴로 베이스에 얹히는 방식
- [IMS hierarchical DB](ims_hierarchical_db.md) — HIDAM 보류승인 DB(루트 PAUTSUM0/자식 PAUTDTL1+인덱스), DBD/PSB/PCB, 두 접근법(EXEC DLI GU/GNP/ISRT/REPL vs CALL CBLTDLI+SSA), 계층형↔Java 매핑
- [Statement generation CBSTM03](statement_generation_cbstm03.md) — CBSTM03A(드라이버)+CBSTM03B(범용 I/O 서브루틴) 분담, ALTER/GO TO 디스패처, 2차원 카드/거래 테이블 조인, 텍스트+HTML 동시 출력
- [Auth MQ/DB2 2PC](auth_mq_db2_2pc.md) — COPAUA0C(CP00 MQ트리거 승인처리,MQ는 NO-SYNCPOINT) + 사기표시 2PC(COPAUS1C 오케스트레이터+COPAUS2C DB2워커), EXEC CICS SYNCPOINT가 IMS+DB2 원자커밋↔JTA
- [Transaction report CBTRN03C](transaction_report_cbtrn03c.md) — 거래 상세 리포트 배치: DATEPARM 일자필터+4파일 RANDOM 룩업 조인+카드단위 control-break 합계+페이지네이션, 리포트 레이아웃 copybook CVTRA07Y
