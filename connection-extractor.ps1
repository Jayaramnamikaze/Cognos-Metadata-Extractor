Add-Type -AssemblyName System.IO.Compression

$serverInstance = "tcp:34.172.173.103,1433"
$UserID="cognos"
$Password="cognos"
$database = "CS"
$outputDir = "cognos_connection_export"

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
Write-Host "=== CONNECTION EXTRACTOR ===" -ForegroundColor Cyan

$query = @"
SELECT 
    cmo.CMID,
    names.NAME AS ConnectionName,
    props52.CONNECTSTR AS JdbcUrl
FROM CMOBJECTS cmo
JOIN CMOBJPROPS52 props52 
    ON cmo.CMID = props52.CMID
LEFT JOIN CMOBJNAMES names 
    ON names.CMID = cmo.CMID
WHERE cmo.CLASSID = 9;
"@


$connection = New-Object System.Data.SqlClient.SqlConnection
$connection.ConnectionString = "Server=$serverInstance;Database=$database;User ID=$UserID;Password=$Password;TrustServerCertificate=True;"

$command = $connection.CreateCommand()
$command.CommandText = $query

try {
    $connection.Open()
    Write-Host "Connected to database" -ForegroundColor Green
    $reader = $command.ExecuteReader()

    while ($reader.Read()) {

        $cmid = $reader["CMID"]
        $name = $reader["ConnectionName"]
        $jdbc = $reader["JdbcUrl"]

        # Sanitize name for file
        $safeName = ($name -replace '[^a-zA-Z0-9_\-]', '_')
        if (-not $safeName) { $safeName = "connection_$cmid" }

        # Output path
        $filePath = Join-Path $outputDir "connection_${safeName}_$cmid.json"

        # CREATE JSON OBJECT
        $jsonObj = [PSCustomObject]@{
            CMID = $cmid
            ConnectionName = $name
            JdbcUrl = $jdbc
        }

        # Convert to JSON
        $jsonObj | ConvertTo-Json -Depth 10 | Out-File -FilePath $filePath -Encoding UTF8

        Write-Host "[OK] Saved JSON → $filePath" -ForegroundColor Green
    }
}
catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    if ($connection.State -eq 'Open') { $connection.Close() }
}
