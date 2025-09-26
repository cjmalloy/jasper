#!/bin/bash

# Generate index page for all Gatling reports
REPORT_DIR="/report"
INDEX_FILE="$REPORT_DIR/index.html"

# Create report directory if it doesn't exist
mkdir -p "$REPORT_DIR"

# Start HTML
cat > "$INDEX_FILE" << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Jasper Load Test Reports</title>
    <style>
        body { 
            font-family: Arial, sans-serif; 
            margin: 40px; 
            background-color: #f5f5f5;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
            background: white;
            padding: 30px;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 { 
            color: #2c3e50; 
            border-bottom: 3px solid #3498db;
            padding-bottom: 10px;
        }
        h2 { 
            color: #34495e; 
            margin-top: 30px;
        }
        .simulation-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin: 20px 0;
        }
        .simulation-card {
            border: 1px solid #ddd;
            border-radius: 8px;
            padding: 20px;
            background: #fafafa;
            transition: box-shadow 0.3s ease;
        }
        .simulation-card:hover {
            box-shadow: 0 4px 15px rgba(0,0,0,0.1);
        }
        .simulation-title {
            font-size: 1.2em;
            font-weight: bold;
            color: #2c3e50;
            margin-bottom: 10px;
        }
        .simulation-description {
            color: #7f8c8d;
            margin-bottom: 15px;
            line-height: 1.4;
        }
        .report-link {
            display: inline-block;
            padding: 10px 20px;
            background: #3498db;
            color: white;
            text-decoration: none;
            border-radius: 5px;
            transition: background 0.3s ease;
        }
        .report-link:hover {
            background: #2980b9;
        }
        .unavailable {
            color: #e74c3c;
            font-style: italic;
        }
        .footer {
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid #eee;
            text-align: center;
            color: #7f8c8d;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 Jasper Knowledge Management Server - Load Test Reports</h1>
        
        <h2>📊 Test Scenarios</h2>
        <div class="simulation-grid">
EOF

# Define simulation information
declare -A simulations=(
    ["SimpleJasperSimulation"]="Smoke Test|Quick validation of core functionality (45s, >75% success)"
    ["ComprehensiveJasperSimulation"]="Comprehensive Test|Realistic knowledge management workflows (3min, >75% success)"  
    ["UserJourneySimulation"]="User Journey Test|Real user journey patterns with research and collaboration (5min, >75% success)"
    ["StressTestSimulation"]="Stress Test|System limits and edge cases testing (4min, >70% success)"
)

# Check for reports and generate cards
for simulation in SimpleJasperSimulation ComprehensiveJasperSimulation UserJourneySimulation StressTestSimulation; do
    IFS='|' read -r title description <<< "${simulations[$simulation]}"
    
    # Find the most recent report directory for this simulation
    report_dir=$(find /app/gatling/target/gatling -name "${simulation,,}*" -type d 2>/dev/null | head -1)
    
    cat >> "$INDEX_FILE" << EOF
            <div class="simulation-card">
                <div class="simulation-title">$title</div>
                <div class="simulation-description">$description</div>
EOF

    if [ -n "$report_dir" ] && [ -f "$report_dir/index.html" ]; then
        # Copy report to main report directory
        sim_report_dir="$REPORT_DIR/$(basename $report_dir)"
        cp -r "$report_dir" "$sim_report_dir"
        
        cat >> "$INDEX_FILE" << EOF
                <a href="$(basename $report_dir)/index.html" class="report-link">📈 View Report</a>
EOF
    else
        cat >> "$INDEX_FILE" << EOF
                <span class="unavailable">❌ Report not available</span>
EOF
    fi
    
    cat >> "$INDEX_FILE" << EOF
            </div>
EOF
done

# Complete HTML
cat >> "$INDEX_FILE" << 'EOF'
        </div>

        <h2>📋 Test Summary</h2>
        <p>These load tests validate the Jasper Knowledge Management Server under various scenarios:</p>
        <ul>
            <li><strong>Simple:</strong> Basic smoke test for CI/CD validation</li>
            <li><strong>Comprehensive:</strong> Full workflow testing with realistic user patterns</li>
            <li><strong>User Journey:</strong> End-to-end user experience validation</li>
            <li><strong>Stress:</strong> Performance limits and concurrent user testing</li>
        </ul>

        <div class="footer">
            <p>Generated by Jasper Gatling Load Test Suite</p>
        </div>
    </div>
</body>
</html>
EOF

echo "✅ Gatling reports index generated at: $INDEX_FILE"

# List available reports
echo "📁 Available reports:"
find "$REPORT_DIR" -name "index.html" | sed 's|/report/||' | sort