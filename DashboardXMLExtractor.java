// DashboardXMLExtractor.java
// Compatible with IBM Cognos Analytics 11.1.x SDK
import com.cognos.developer.schemas.bibus._3.*;
import java.net.URL;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DashboardXMLExtractor {
    
    private static ContentManagerService_PortType cmService;
    private static String dispatcherURL = "http://34.172.173.103:9300/p2pd/servlet/dispatch";
    public static void main(String[] args) {
        // String dispatcherURL = "http://34.172.173.103:9300/bi/?perspective=home";
        
        try {
            System.out.println("=== COGNOS 11.1 DASHBOARD XML EXTRACTOR ===");
            System.out.println("Connecting to: " + dispatcherURL);
            
            ContentManagerService_ServiceLocator cmLocator = 
                new ContentManagerService_ServiceLocator();
            
            // cmService = cmLocator.getContentManagerService(new URL(dispatcherURL));
            cmService = cmLocator.getcontentManagerService(); // no parameters

            // Set the endpoint URL
            ((javax.xml.rpc.Stub) cmService)._setProperty(
                javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, dispatcherURL
            );
            System.out.println("Connected successfully\n");
            
            // Debug: Print ALL methods to see what's available
            System.out.println("=== ContentManagerService ALL methods ===");
            java.lang.reflect.Method[] allMethods = cmService.getClass().getMethods();
            System.out.println("Total methods: " + allMethods.length);
            for (java.lang.reflect.Method m : allMethods) {
                String methodName = m.getName();
                // Print methods that might be relevant
                if (!methodName.startsWith("get") && 
                    !methodName.startsWith("set") && 
                    !methodName.equals("equals") &&
                    !methodName.equals("hashCode") &&
                    !methodName.equals("toString") &&
                    !methodName.equals("wait") &&
                    !methodName.equals("notify") &&
                    !methodName.equals("notifyAll")) {
                    
                    System.out.print("  " + methodName + "(");
                    Class<?>[] params = m.getParameterTypes();
                    for (int i = 0; i < params.length; i++) {
                        System.out.print(params[i].getSimpleName());
                        if (i < params.length - 1) System.out.print(", ");
                    }
                    System.out.println(")");
                }
            }
            System.out.println();
            
            // Create output directory
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String outputDir = "cognos_dashboards_" + timestamp;
            new File(outputDir).mkdir();
            System.out.println("Output directory: " + outputDir + "\n");
            
            System.out.println("=== Searching for Cognos 11 content ===\n");
            
            int totalCount = 0;
            
            // Search for Explorations (Cognos 11 Dashboards)
            totalCount += searchAndExtract("//exploration", "Explorations (Dashboards)", 
                                          outputDir, totalCount);
            
            // Search for Reports
            totalCount += searchAndExtract("//report", "Reports", outputDir, totalCount);
            
            System.out.println("\n=== SUMMARY ===");
            System.out.println("Total items extracted: " + totalCount);
            System.out.println("Files saved in: " + outputDir);
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static int searchAndExtract(String searchPath, String typeName, 
                                        String outputDir, int startCount) {
        System.out.println("Searching for: " + typeName);
        System.out.println("Query: " + searchPath);
        
        int count = 0;
        
        try {
            PropEnum[] properties = new PropEnum[] {
                PropEnum.searchPath,
                PropEnum.defaultName,
                PropEnum.storeID,
                PropEnum.creationTime,
                PropEnum.modificationTime
            };
            SearchPathMultipleObject sp = new SearchPathMultipleObject();
            sp.set_value(searchPath);
            BaseClass[] results = cmService.query(
                sp,
                properties,
                new Sort[] {},
                new QueryOptions()
            );
            
            System.out.println("Found " + results.length + " " + typeName + "\n");
            
            for (BaseClass item : results) {
                count++;
                int itemNumber = startCount + count;
                
                String name = "Unnamed";
                String path = "Unknown";
                String storeId = "unknown";
                
                if (item.getDefaultName() != null && item.getDefaultName().getValue() != null) {
                    name = item.getDefaultName().getValue();
                }
                if (item.getSearchPath() != null && item.getSearchPath().getValue() != null) {
                    path = item.getSearchPath().getValue();
                }
                if (item.getStoreID() != null && item.getStoreID().getValue() != null) {
                    storeId = item.getStoreID().getValue().get_value();
                }
                
                System.out.println("Item #" + itemNumber + ":");
                System.out.println("  Name: " + name);
                System.out.println("  Type: " + item.getClass().getSimpleName());
                System.out.println("  Path: " + path);
                
                // Debug: Check if getSpecification exists and what it returns
                try {
                    java.lang.reflect.Method getSpec = item.getClass().getMethod("getSpecification");
                    Object specObj = getSpec.invoke(item);
                    
                    if (specObj == null) {
                        System.out.println("  getSpecification() returned NULL");
                    } else {
                        System.out.println("  getSpecification() returned: " + specObj.getClass().getName());
                        
                        // Try to get the value
                        try {
                            java.lang.reflect.Method getValue = specObj.getClass().getMethod("getValue");
                            Object value = getValue.invoke(specObj);
                            if (value == null) {
                                System.out.println("  getValue() returned NULL");
                            } else {
                                System.out.println("  getValue() returned type: " + value.getClass().getName());
                                if (value instanceof String) {
                                    String str = (String) value;
                                    System.out.println("  Value length: " + str.length());
                                    System.out.println("  First 100 chars: " + str.substring(0, Math.min(100, str.length())));
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("  No getValue() method or error: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("  Error checking getSpecification(): " + e.getMessage());
                }
                
                // Save metadata
                saveMetadata(outputDir, itemNumber, name, path, storeId, item);
                
                // Try to get the specification using query with more properties
                String specData = getSpecificationData(path, item);
                
                if (specData != null && specData.length() > 0) {
                    // Determine if it's JSON or XML
                    String trimmed = specData.trim();
                    boolean isJson = trimmed.startsWith("{") || trimmed.startsWith("[");
                    String fileExt = isJson ? ".json" : ".xml";
                    
                    // Pretty print if JSON
                    if (isJson) {
                        try {
                            specData = prettyPrintJson(specData);
                        } catch (Exception e) {
                            System.out.println("  Could not pretty-print JSON: " + e.getMessage());
                        }
                    }
                    
                    saveSpecification(outputDir, itemNumber, name, path, specData, fileExt);
                    System.out.println("  Status: " + (isJson ? "JSON" : "XML") + " specification extracted successfully");
                } else {
                    System.out.println("  Status: No specification available");
                }
                
                System.out.println();
            }
            
        } catch (Exception e) {
            System.out.println("Error searching " + typeName + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return count;
    }
    
    private static String getSpecificationData(String path, BaseClass originalItem) {
        try {
            // Try checking the original item first (without re-querying)
            System.out.println("  Checking properties on original object:");
            java.lang.reflect.Method[] methods = originalItem.getClass().getMethods();
            
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().startsWith("get") && 
                    method.getParameterCount() == 0 &&
                    !method.getName().equals("getClass") &&
                    !method.getName().equals("getTypeDesc")) {
                    
                    try {
                        Object result = method.invoke(originalItem);
                        if (result != null) {
                            String resultType = result.getClass().getSimpleName();
                            
                            // Only print non-common properties
                            if (!method.getName().equals("getSearchPath") &&
                                !method.getName().equals("getDefaultName") &&
                                !method.getName().equals("getStoreID") &&
                                !method.getName().equals("getCreationTime") &&
                                !method.getName().equals("getModificationTime") &&
                                !method.getName().equals("getObjectClass") &&
                                !method.getName().equals("getParent") &&
                                !method.getName().equals("getOwner")) {
                                
                                System.out.println("    " + method.getName() + "() = " + resultType);
                            }
                            
                            // Try to extract any data that looks like XML or JSON
                            String data = tryExtractData(result, method.getName());
                            if (data != null && data.length() > 100) {
                                return data;
                            }
                        }
                    } catch (Exception e) {
                        // Skip methods that fail
                    }
                }
            }
            
            // Try a targeted re-query with only safe properties
            System.out.println("  Trying targeted query with common properties...");
            SearchPathSingleObject objRef = new SearchPathSingleObject();
            objRef.set_value(path);
            
            PropEnum[] safeProps = new PropEnum[] {
                PropEnum.searchPath,
                PropEnum.defaultName,
                PropEnum.storeID,
                PropEnum.specification,
                PropEnum.data,
                PropEnum.description,
                PropEnum.contact,
                PropEnum.contactEMail,
                PropEnum.usage
            };
            
            try {
                // BaseClass[] detailedResults = cmService.query(
                //     new SearchPathMultipleObject(path),
                //     safeProps,
                //     new Sort[] {},
                //     new QueryOptions()
                // );
                SearchPathMultipleObject sp2 = new SearchPathMultipleObject();
                sp2.set_value(path);

                BaseClass[] detailedResults = cmService.query(
                    sp2,
                    safeProps,
                    new Sort[] {},
                    new QueryOptions()
                );

                
                if (detailedResults != null && detailedResults.length > 0) {
                    System.out.println("  Re-query successful, checking again...");
                    String data = checkAllGetters(detailedResults[0]);
                    if (data != null) {
                        return data;
                    }
                }
            } catch (Exception e) {
                System.out.println("  Re-query failed: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
        
        return null;
    }
    
    private static String checkAllGetters(BaseClass obj) {
        try {
            java.lang.reflect.Method[] methods = obj.getClass().getMethods();
            
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
                    try {
                        Object result = method.invoke(obj);
                        if (result != null) {
                            String data = tryExtractData(result, method.getName());
                            if (data != null && data.length() > 100) {
                                return data;
                            }
                        }
                    } catch (Exception e) {
                        // Skip
                    }
                }
            }
        } catch (Exception e) {
            // Failed
        }
        return null;
    }
    
    private static String tryExtractData(Object obj, String methodName) {
        try {
            // Direct string
            if (obj instanceof String) {
                String str = (String) obj;
                if (str.trim().startsWith("<") || str.trim().startsWith("{")) {
                    System.out.println("      -> Found potential data in " + methodName + "!");
                    return str;
                }
            }
            
            // Byte array
            if (obj instanceof byte[]) {
                String str = new String((byte[]) obj, "UTF-8");
                if (str.trim().startsWith("<") || str.trim().startsWith("{")) {
                    System.out.println("      -> Found potential data in " + methodName + "!");
                    return str;
                }
            }
            
            // Try getValue()
            try {
                java.lang.reflect.Method getValue = obj.getClass().getMethod("getValue");
                Object value = getValue.invoke(obj);
                if (value != null) {
                    return tryExtractData(value, methodName + ".getValue");
                }
            } catch (Exception e) {
                // No getValue
            }
            
        } catch (Exception e) {
            // Failed
        }
        
        return null;
    }
    
    private static String extractXMLFromObject(BaseClass obj) {
        try {
            java.lang.reflect.Method[] methods = obj.getClass().getMethods();
            
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();
                
                // Look for getter methods that might contain XML
                if ((methodName.equals("getSpecification") || 
                     methodName.equals("getData") ||
                     methodName.equals("getReport") ||
                     methodName.equals("getModel")) && 
                    method.getParameterCount() == 0) {
                    
                    try {
                        Object result = method.invoke(obj);
                        
                        if (result == null) {
                            continue;
                        }
                        
                        System.out.println("  Found method: " + methodName + "(), result type: " + result.getClass().getName());
                        
                        // If it's a string directly
                        if (result instanceof String) {
                            String str = (String) result;
                            if (str.trim().startsWith("<") && str.length() > 100) {
                                System.out.println("  Extracted XML from " + methodName + "() as String");
                                return str;
                            }
                        }
                        
                        // If it's a byte array
                        if (result instanceof byte[]) {
                            String str = new String((byte[]) result, "UTF-8");
                            if (str.trim().startsWith("<") && str.length() > 100) {
                                System.out.println("  Extracted XML from " + methodName + "() as byte[]");
                                return str;
                            }
                        }
                        
                        // If it's wrapped in another object, try getValue()
                        try {
                            java.lang.reflect.Method getValue = 
                                result.getClass().getMethod("getValue");
                            Object value = getValue.invoke(result);
                            
                            if (value == null) {
                                continue;
                            }
                            
                            System.out.println("  Found getValue(), value type: " + value.getClass().getName());
                            
                            if (value instanceof String) {
                                String str = (String) value;
                                if (str.trim().startsWith("<") && str.length() > 100) {
                                    System.out.println("  Extracted XML from " + methodName + "().getValue() as String");
                                    return str;
                                }
                            }
                            
                            if (value instanceof byte[]) {
                                String str = new String((byte[]) value, "UTF-8");
                                if (str.trim().startsWith("<") && str.length() > 100) {
                                    System.out.println("  Extracted XML from " + methodName + "().getValue() as byte[]");
                                    return str;
                                }
                            }
                        } catch (NoSuchMethodException e) {
                            // No getValue method, try get_value()
                            try {
                                java.lang.reflect.Method get_value = 
                                    result.getClass().getMethod("get_value");
                                Object value = get_value.invoke(result);
                                
                                if (value != null) {
                                    System.out.println("  Found get_value(), value type: " + value.getClass().getName());
                                    
                                    if (value instanceof String) {
                                        String str = (String) value;
                                        if (str.trim().startsWith("<") && str.length() > 100) {
                                            System.out.println("  Extracted XML from " + methodName + "().get_value() as String");
                                            return str;
                                        }
                                    }
                                    
                                    if (value instanceof byte[]) {
                                        String str = new String((byte[]) value, "UTF-8");
                                        if (str.trim().startsWith("<") && str.length() > 100) {
                                            System.out.println("  Extracted XML from " + methodName + "().get_value() as byte[]");
                                            return str;
                                        }
                                    }
                                }
                            } catch (NoSuchMethodException e2) {
                                // Neither getValue nor get_value exists
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("  Error invoking " + methodName + "(): " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("  Reflection error: " + e.getMessage());
        }
        
        return null;
    }
    
    private static void saveSpecification(String outputDir, int itemNumber, String name, 
                                          String path, String content, String fileExt) {
        try {
            String fileName = outputDir + "/item_" + itemNumber + "_" + 
                name.replaceAll("[^a-zA-Z0-9]", "_") + fileExt;
            
            PrintWriter writer = new PrintWriter(fileName, "UTF-8");
            
            if (fileExt.equals(".xml")) {
                writer.println("<!-- Name: " + name + " -->");
                writer.println("<!-- Path: " + path + " -->");
                writer.println();
            } else if (fileExt.equals(".json")) {
                // Add metadata as JSON comment (not standard but helpful)
                writer.println("// Name: " + name);
                writer.println("// Path: " + path);
                writer.println();
            }
            
            writer.println(content);
            writer.close();
            
            System.out.println("  Saved to: " + fileName);
            System.out.println("  Size: " + content.length() + " characters");
            
        } catch (Exception e) {
            System.out.println("  Error saving specification: " + e.getMessage());
        }
    }
    
    private static String prettyPrintJson(String json) {
        // Simple JSON pretty printer
        StringBuilder result = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        boolean escapeNext = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escapeNext) {
                result.append(c);
                escapeNext = false;
                continue;
            }
            
            if (c == '\\') {
                result.append(c);
                escapeNext = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                result.append(c);
                continue;
            }
            
            if (inString) {
                result.append(c);
                continue;
            }
            
            switch (c) {
                case '{':
                case '[':
                    result.append(c);
                    result.append('\n');
                    indent++;
                    appendIndent(result, indent);
                    break;
                case '}':
                case ']':
                    result.append('\n');
                    indent--;
                    appendIndent(result, indent);
                    result.append(c);
                    break;
                case ',':
                    result.append(c);
                    result.append('\n');
                    appendIndent(result, indent);
                    break;
                case ':':
                    result.append(c);
                    result.append(' ');
                    break;
                default:
                    if (!Character.isWhitespace(c)) {
                        result.append(c);
                    }
            }
        }
        
        return result.toString();
    }
    
    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
    }
    
    private static void saveMetadata(String outputDir, int itemNumber, String name, 
                                     String path, String storeId, BaseClass item) {
        try {
            String metaFile = outputDir + "/item_" + itemNumber + "_metadata.txt";
            PrintWriter writer = new PrintWriter(metaFile, "UTF-8");
            
            writer.println("Item Metadata");
            writer.println("=============");
            writer.println("Name: " + name);
            writer.println("Path: " + path);
            writer.println("Type: " + item.getClass().getSimpleName());
            writer.println("StoreID: " + storeId);
            
            if (item.getCreationTime() != null && item.getCreationTime().getValue() != null) {
                writer.println("Created: " + item.getCreationTime().getValue().getTime());
            }
            if (item.getModificationTime() != null && item.getModificationTime().getValue() != null) {
                writer.println("Modified: " + item.getModificationTime().getValue().getTime());
            }
            
            // List all available methods for debugging
            writer.println("\nAvailable methods:");
            java.lang.reflect.Method[] methods = item.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().startsWith("get") && 
                    method.getParameterCount() == 0 &&
                    !method.getName().equals("getClass")) {
                    writer.println("  - " + method.getName() + "()");
                }
            }
            
            writer.close();
            
        } catch (Exception e) {
            System.out.println("  Error saving metadata: " + e.getMessage());
        }
    }
}

