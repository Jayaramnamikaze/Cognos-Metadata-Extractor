import java.net.URL;
import java.io.File;
import java.io.FileWriter;

import org.apache.axis.client.Stub;

import com.cognos.developer.schemas.bibus._3.*;

public class ExtractActiveReportsXML {

    public static void main(String[] args) {

        String dispatcherURL =
            "http://34.172.173.103:9300/p2pd/servlet/dispatch";

        String outputDir = "active_reports_xml";

        try {
            // =====================================================
            // 1. Create output directory
            // =====================================================
            File dir = new File(outputDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // =====================================================
            // 2. Cognos Content Manager connection
            // =====================================================
            ContentManagerService_ServiceLocator cmLocator =
                new ContentManagerService_ServiceLocator();

            ContentManagerService_PortType cmService =
                cmLocator.getcontentManagerService(new URL(dispatcherURL));

            // =====================================================
            // 3. Anonymous login
            // =====================================================
            CAM cam = new CAM();
            cam.setAction("logon");

            HdrSession session = new HdrSession();
            session.setFormFieldVars(new FormFieldVar[0]);

            BiBusHeader header = new BiBusHeader();
            header.setCAM(cam);
            header.setHdrSession(session);

            ((Stub) cmService).setHeader(
                "http://developer.cognos.com/schemas/bibus/3/",
                "biBusHeader",
                header
            );

            // =====================================================
            // 4. Query ALL Active Reports
            // =====================================================
            BaseClass[] results = cmService.query(
                new SearchPathMultipleObject("//interactiveReport"),
                new PropEnum[] {
                    PropEnum.defaultName,
                    PropEnum.specification
                },
                null,
                new QueryOptions()
            );

            // =====================================================
            // 5. Write XML to files
            // =====================================================
            for (BaseClass bc : results) {

                if (!(bc instanceof InteractiveReport)) {
                    continue;
                }

                InteractiveReport rpt = (InteractiveReport) bc;

                if (rpt.getSpecification() == null) {
                    continue;
                }

                String reportName =
                    rpt.getDefaultName().getValue();

                String fileName =
                    safeFileName(reportName) + ".xml";

                String xml =
                    rpt.getSpecification().getValue();

                FileWriter writer =
                    new FileWriter(new File(dir, fileName));

                writer.write(xml);
                writer.close();

                System.out.println("Saved XML: " + fileName);
            }

            System.out.println("\n✅ All Active Report XML files exported.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================
    // Helper: sanitize filenames
    // =========================================================
    private static String safeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
