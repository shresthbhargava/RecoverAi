# send-test-webhook.ps1
#
# Sends a correctly signed fake Razorpay webhook at your local backend, so the whole
# reconciliation path can be demoed without waiting on a real card payment.
#
# The one thing this script exists to get right: the bytes that get signed and the bytes
# that get sent are the SAME bytes. It builds a UTF-8 byte array once, computes the HMAC
# over that array, and posts that array. Passing a plain string to Invoke-RestMethod
# instead lets PowerShell pick its own encoding, and on 5.1 that is ISO-8859-1, which
# silently produces a signature mismatch that looks exactly like a wrong secret.
#
# Usage:
#   .\send-test-webhook.ps1 -OrderId order_ABC123 -Secret whsec_test_123
#   .\send-test-webhook.ps1 -OrderId order_ABC123 -Secret whsec_test_123 -Tamper
#   .\send-test-webhook.ps1 -PaymentLinkId plink_XYZ -Secret whsec_test_123 -Amount 1500
#
# Get a real OrderId from your own data first:
#   select external_ref, action, status from recovery_attempt where external_ref is not null;

param(
    [string] $OrderId,
    [string] $PaymentLinkId,
    [Parameter(Mandatory = $true)][string] $Secret,
    [decimal] $Amount = 500,
    [string] $Url = "http://localhost:8080/api/webhooks/razorpay",
    [string] $EventId,
    [ValidateSet("payment.captured", "payment_link.paid", "order.paid", "payment.failed")]
    [string] $Event = "payment.captured",
    [switch] $Tamper
)

if (-not $OrderId -and -not $PaymentLinkId) {
    Write-Error "Give either -OrderId or -PaymentLinkId. These must match a recovery_attempt.external_ref value or the webhook will correctly come back IGNORED."
    exit 1
}

# Razorpay sends amounts as integer paise, never rupees.
$paise = [long]($Amount * 100)
$paymentId = "pay_test$([guid]::NewGuid().ToString('N').Substring(0,10))"

if (-not $EventId) { $EventId = "evt_test$([guid]::NewGuid().ToString('N').Substring(0,12))" }

$paymentEntity = [ordered]@{
    id       = $paymentId
    entity   = "payment"
    amount   = $paise
    currency = "INR"
    status   = if ($Event -eq "payment.failed") { "failed" } else { "captured" }
    order_id = $OrderId
    method   = "card"
}
if ($Event -eq "payment.failed") {
    $paymentEntity["error_description"] = "Test failure injected by send-test-webhook.ps1"
}

$payload = [ordered]@{ payment = [ordered]@{ entity = $paymentEntity } }

if ($PaymentLinkId) {
    $payload["payment_link"] = [ordered]@{
        entity = [ordered]@{ id = $PaymentLinkId; status = "paid"; amount = $paise; amount_paid = $paise }
    }
}
if ($OrderId) {
    $payload["order"] = [ordered]@{
        entity = [ordered]@{ id = $OrderId; status = "paid"; amount = $paise; amount_paid = $paise }
    }
}

$body = [ordered]@{
    entity     = "event"
    account_id = "acc_test0000000000"
    event      = $Event
    contains   = @("payment")
    payload    = $payload
} | ConvertTo-Json -Depth 10 -Compress

# Sign and send the identical byte array. Do not refactor this into a string round-trip.
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($Secret)
$signature = ($hmac.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""

if ($Tamper) {
    # Flip one hex character so the HMAC is structurally valid but wrong. This is the
    # negative test: it should come back 400 with an INVALID_SIGNATURE row recorded.
    $last = $signature.Substring($signature.Length - 1)
    $signature = $signature.Substring(0, $signature.Length - 1) + $(if ($last -eq "a") { "b" } else { "a" })
    Write-Host "Signature deliberately corrupted - expecting HTTP 400" -ForegroundColor Yellow
}

Write-Host "POST $Url"
Write-Host "  event    : $Event"
Write-Host "  event id : $EventId"
Write-Host "  ref      : $(if ($PaymentLinkId) { $PaymentLinkId } else { $OrderId })"
Write-Host ("  amount   : {0} INR ({1} paise)" -f $Amount, $paise)
Write-Host ""

try {
    $response = Invoke-RestMethod -Uri $Url -Method Post -Body $bytes `
        -ContentType "application/json" `
        -Headers @{ "X-Razorpay-Signature" = $signature; "X-Razorpay-Event-Id" = $EventId }
    Write-Host "HTTP 200" -ForegroundColor Green
    $response | ConvertTo-Json -Depth 5
}
catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Host "HTTP $status" -ForegroundColor $(if ($Tamper -and $status -eq 400) { "Green" } else { "Red" })
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    else { Write-Host $_.Exception.Message }
}

Write-Host ""
Write-Host "Delivery log: curl http://localhost:8080/api/webhooks/recent"
Write-Host "Counters    : curl http://localhost:8080/api/webhooks/stats"
