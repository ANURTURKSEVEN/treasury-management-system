-- =====================================================================
-- ÖRNEK CB SMG HATA KAYDI  (error_log tablosuna)
-- Workbench'te  SELECT * FROM error_log ORDER BY id DESC;  yapınca
-- error_message alanında tam CBException stack trace görünür.
-- =====================================================================
USE treasury_db;

INSERT INTO error_log (error_type, error_source, error_caller, error_message, username)
VALUES (
    'cb.smg.general.utility.CBException',
    'cb.smg.general.utility.CBQueryExecuter.executeQuery : 2772',
    'cb.cpn.bankcheques.bean.BankChequeServices.getCustomerAvailableChequeBookLimit : 246',
'cb.smg.general.utility.CBException:  ( 23 ) Sorgulama hatası. Hata kodu = ORA-1427
	at cb.smg.general.utility.CBQueryExecuter.executeQuery(CBQueryExecuter.java:2772)
	at cb.cpn.bankcheques.bean.BankChequeServices.getCustomerAvailableChequeBookLimit(BankChequeServices.java:246)
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:498)
	at cb.smg.general.utility.CBCaller.call(CBCaller.java:382)
	at cb.smg.general.utility.CBTSM.executeFWDTrx(CBTSM.java:411)
	at cb.smg.general.utility.CBTSM.execute(CBTSM.java:233)
	at cb.smg.process.management.OLTPProcess.execute(OLTPProcess.java:100)
	at cb.smg.plm.session.CBProcessDispatcher.execute(CBProcessDispatcher.java:93)
	at cb.smg.process.management.CBProcess.startProcess(CBProcess.java:205)
	at cb.smg.process.management.CBProcess.run(CBProcess.java:232)
	at java.lang.Thread.run(Thread.java:748)',
    'admin'
);
