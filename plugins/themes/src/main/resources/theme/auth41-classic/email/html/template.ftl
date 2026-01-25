<#macro emailLayout>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style type="text/css">
        body {
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            background-color: #f7fafc;
        }
        .email-container {
            max-width: 600px;
            margin: 0 auto;
            background-color: #ffffff;
        }
        .email-header {
            background-color: #1e3a5f;
            padding: 30px 20px;
            text-align: center;
        }
        .email-header h1 {
            color: #ffffff;
            margin: 0;
            font-size: 24px;
            font-weight: 600;
        }
        .email-body {
            padding: 40px 30px;
            color: #2d3748;
            line-height: 1.6;
        }
        .email-button {
            display: inline-block;
            padding: 14px 28px;
            background-color: #1e3a5f;
            color: #ffffff !important;
            text-decoration: none;
            border-radius: 8px;
            font-weight: 600;
            margin: 20px 0;
        }
        .email-footer {
            background-color: #f7fafc;
            padding: 20px;
            text-align: center;
            font-size: 12px;
            color: #718096;
        }
    </style>
</head>
<body>
    <div class="email-container">
        <div class="email-header">
            <h1>Auth41 Corporate Portal</h1>
        </div>
        <div class="email-body">
            <#nested>
        </div>
        <div class="email-footer">
            <p>&copy; ${.now?string('yyyy')} Auth41 Corporate Portal. All rights reserved.</p>
        </div>
    </div>
</body>
</html>
</#macro>
