$Server = "172.17.133.47"

$Ports = @(
    @{ Name = "Redis"; Port = 6379 },
    @{ Name = "Kafka"; Port = 9092 },
    @{ Name = "Neo4j Web UI"; Port = 7474 },
    @{ Name = "Neo4j Bolt API"; Port = 7687 }
)

Write-Host "========================================="
Write-Host "Testing AIOps Server Ports: $Server"
Write-Host "========================================="

foreach ($item in $Ports) {
    $service = $item.Name
    $port = $item.Port

    Write-Host ""
    Write-Host "Testing $service on $Server`:$port ..."

    $result = Test-NetConnection -ComputerName $Server -Port $port -WarningAction SilentlyContinue

    if ($result.TcpTestSucceeded -eq $true) {
        Write-Host "SUCCESS: $service port $port is reachable" -ForegroundColor Green
    }
    else {
        Write-Host "FAILED: $service port $port is not reachable" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================="
Write-Host "Test completed."
Write-Host "========================================="