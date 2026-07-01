/**
 * Microsoft SCOM alert collection (causal funnel Stage 0). {@link org.kfh.aiops.ingestion.scom.ScomWinRmClient}
 * shells out to the local {@code powershell.exe}, opens a WinRM/PS-remoting session to the SCOM
 * management server, runs {@code Get-SCOMAlert}, and parses the compressed JSON back (proven flow in
 * docs/SCOM_Collectors.md — handles WCF {@code /Date(ms)/}, the Kuwait +3h over-fetch, and dedup by
 * alert Id). The {@link org.kfh.aiops.ingestion.scom.ScomCollector} feeds the raw maps through the
 * shared {@link org.kfh.aiops.ingestion.IngestionService} (normalize → dedup → index).
 *
 * <p><b>Windows-only:</b> collection spawns the native PowerShell binary, so it runs on the Windows
 * Tomcat host where the OperationsManager module and PS remoting to the SCOM MS are reachable.
 * Credentials come from configuration/environment only and are never committed or logged.
 */
package org.kfh.aiops.ingestion.scom;
