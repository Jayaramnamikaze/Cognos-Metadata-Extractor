Add-Type -AssemblyName System.IO.Compression
$serverInstance = "tcp:34.172.173.103,1433"
$UserID="cognos"
$Password="cognos"
$database = "CS"
$outputDir = "cognos_modules_export"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
Write-Host "=== DATA MODULE EXTRACTOR ===" -ForegroundColor Cyan
$query = @"
SELECT 
    cmo.CMID, 
    names.NAME,
    props85.DATA AS ModuleData
FROM CMOBJECTS cmo
JOIN CMOBJPROPS85 props85 ON cmo.CMID = props85.CMID
LEFT JOIN CMOBJNAMES names ON names.CMID = cmo.CMID AND names.LOCALEID = -1
WHERE cmo.CLASSID = 261;
"@
$connection = New-Object System.Data.SqlClient.SqlConnection
$connection.ConnectionString = "Server=$serverInstance;Database=$database;User ID=$UserID;Password=$Password;TrustServerCertificate=True;"
$command = $connection.CreateCommand()
$command.CommandText = $query
function Is-GzipHeader {
    param ([byte[]]$bytes)
    return ($bytes.Length -ge 2 -and $bytes[0] -eq 0x1F -and $bytes[1] -eq 0x8B)
}
function Decompress-GzipBytes {
    param ([byte[]]$bytes)
    $ms = New-Object System.IO.MemoryStream(,$bytes)
    $gz = New-Object System.IO.Compression.GzipStream($ms, [System.IO.Compression.CompressionMode]::Decompress)
    $out = New-Object System.IO.MemoryStream
    $gz.CopyTo($out)
    $text = [System.Text.Encoding]::UTF8.GetString($out.ToArray())
    $gz.Close(); $ms.Close(); $out.Close()
    return $text
}
try {
    $connection.Open()
    Write-Host "Connected to database" -ForegroundColor Green
    $reader = $command.ExecuteReader()
    while ($reader.Read()) {
        $cmid = $reader["CMID"]
        $name = $reader["NAME"]
        $data = $reader["ModuleData"]
        $safeName = ($name -replace '[^a-zA-Z0-9_\-]', '_')
        if (-not $safeName) { $safeName = "module_$cmid" }
        $filePath = Join-Path $outputDir "module_${safeName}_$cmid.json"
        if ($data -is [string] -and $data.StartsWith("H4sI")) {
            Write-Host "[$cmid] Base64 GZIP detected → decoding..." -ForegroundColor Cyan
            $bytes = [System.Convert]::FromBase64String($data)
            $json = Decompress-GzipBytes $bytes
        }
        elseif ($data -is [string]) {
            Write-Host "[$cmid] Plain JSON detected" -ForegroundColor Green
            $json = $data
        }
        else {
            $bytes = [byte[]]$data
            if (Is-GzipHeader $bytes) {
                Write-Host "[$cmid] Binary GZIP detected → decompressing..." -ForegroundColor Cyan
                $json = Decompress-GzipBytes $bytes
            }
            else {
                Write-Host "[$cmid] Byte data → interpreting as UTF8" -ForegroundColor Yellow
                $json = [System.Text.Encoding]::UTF8.GetString($bytes)
            }
        }
        $json | Out-File -FilePath $filePath -Encoding UTF8
        Write-Host "[OK] Saved → $filePath" -ForegroundColor Green
        # Optional quick peek: tell if connections/relationships might be present
        try {
            $obj = $json | ConvertFrom-Json
            $hasConnections = ($obj.connections -ne $null -and $obj.connections.Count -gt 0)
            $hasRelationships = ($obj.relationships -ne $null -and $obj.relationships.Count -gt 0)
            Write-Host ("      connections: {0}, relationships: {1}" -f $hasConnections, $hasRelationships) -ForegroundColor Gray
        } catch {}
    }
}
catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    if ($connection.State -eq 'Open') { $connection.Close() }
}
