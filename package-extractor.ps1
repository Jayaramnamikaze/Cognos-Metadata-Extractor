# Export Data Modules - Fixed Version
Add-Type -AssemblyName System.IO.Compression

$serverInstance = "tcp:34.172.173.103,1433"
$UserID="cognos"
$Password="cognos"
$Encrypt="False"
$TrustServerCertificate="True"
$database = "CS"
$outputDir = "cognos_package_export"

# Create output directory
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

Write-Host "=== DATA MODULE EXTRACTOR ===" -ForegroundColor Cyan
Write-Host "Server: $serverInstance" -ForegroundColor Yellow
Write-Host "Database: $database" -ForegroundColor Yellow
Write-Host "Output: $outputDir" -ForegroundColor Yellow
Write-Host ""

# Your exact query
$query = @"
SELECT 
    cmo.CMID,
    props7.CMODEL
FROM CMOBJECTS cmo
JOIN CMCLASSES cmc ON cmo.CLASSID = cmc.CLASSID
LEFT JOIN CMOBJPROPS7 props7 ON cmo.CMID = props7.CMID
WHERE cmo.CLASSID = 38
  AND props7.CMODEL IS NOT NULL
"@

# Connect to SQL Server
$connection = New-Object System.Data.SqlClient.SqlConnection
$connection.ConnectionString = "Server=$serverInstance;Database=$database;User ID=$UserId;Password=$Password;TrustServerCertificate=$TrustedServerCertificate;"
$command = $connection.CreateCommand()
$command.CommandText = $query

try {
    $connection.Open()
    Write-Host "Connected to database" -ForegroundColor Green
    Write-Host ""
    
    $reader = $command.ExecuteReader()
    
    $count = 0
    $success = 0
    $failed = 0
    
    while ($reader.Read()) {
        $count++
        $cmid = $reader["CMID"]
        $compressedData = [byte[]]$reader["CMODEL"]
        
        Write-Host "[$count] Processing CMID: $cmid" -ForegroundColor White
        
        try {
            # Decompress GZIP
            $memStream = New-Object System.IO.MemoryStream(,$compressedData)
            $gzipStream = New-Object System.IO.Compression.GzipStream($memStream, [System.IO.Compression.CompressionMode]::Decompress)
            $outStream = New-Object System.IO.MemoryStream
            
            $gzipStream.CopyTo($outStream)
            $decompressed = $outStream.ToArray()
            
            # Convert to text
            $text = [System.Text.Encoding]::UTF8.GetString($decompressed)
            
            # Determine extension
            $ext = ".xml"
            if ($text.TrimStart().StartsWith('{') -or $text.TrimStart().StartsWith('[')) {
                $ext = ".json"
            }
            
            # Save file
            $fileName = "module_$cmid$ext"
            $filePath = Join-Path $outputDir $fileName
            
            $text | Out-File -FilePath $filePath -Encoding UTF8
            
            $sizeKB = [math]::Round($text.Length / 1024, 1)
            Write-Host "  [OK] Saved: $fileName ($sizeKB KB)" -ForegroundColor Green
            $success++
            
            # Cleanup
            $gzipStream.Close()
            $memStream.Close()
            $outStream.Close()
            
        } catch {
            Write-Host "  [FAILED] Error: $($_.Exception.Message)" -ForegroundColor Red
            $failed++
        }
    }
    
    $reader.Close()
    
    # Summary
    Write-Host ""
    Write-Host "==================================================" -ForegroundColor Cyan
    Write-Host "EXTRACTION COMPLETE" -ForegroundColor Cyan
    Write-Host "==================================================" -ForegroundColor Cyan
    Write-Host "Total modules:        $count"
    Write-Host "Successfully extracted: $success" -ForegroundColor Green
    Write-Host "Failed:               $failed" -ForegroundColor Red
    Write-Host "Output directory:     $outputDir" -ForegroundColor Yellow
    Write-Host "==================================================" -ForegroundColor Cyan
    
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
} finally {
    if ($connection.State -eq 'Open') {
        $connection.Close()
    }
}
