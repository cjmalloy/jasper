#!/bin/bash

# Generate index page for all Gatling simulation reports
REPORT_DIR="/report"
INDEX_FILE="$REPORT_DIR/index.html"

# Create report directory if it doesn't exist
mkdir -p "$REPORT_DIR"

# Start HTML document
cat > "$INDEX_FILE" << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Jasper Load Test Reports</title>
    <style>
        body { 
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; 
            max-width: 1200px; 
            margin: 0 auto; 
            padding: 20px; 
            background-color: #f5f5f5;
        }
        .header {
            text-align: center;
            margin-bottom: 40px;
            padding: 30px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border-radius: 10px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
        }
        .reports-container {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 40px;
        }
        .report-card {
            background: white;
            border-radius: 10px;
            padding: 25px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
            transition: transform 0.2s, box-shadow 0.2s;
        }
        .report-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 15px rgba(0, 0, 0, 0.2);
        }
        .report-title {
            font-size: 1.4em;
            font-weight: bold;
            color: #333;
            margin-bottom: 10px;
        }
        .report-description {
            color: #666;
            margin-bottom: 15px;
            line-height: 1.5;
        }
        .report-link {
            display: inline-block;
            background: #667eea;
            color: white;
            padding: 10px 20px;
            text-decoration: none;
            border-radius: 5px;
            transition: background-color 0.2s;
        }
        .report-link:hover {
            background: #5a6fd8;
        }
        .footer {
            text-align: center;
            color: #666;
            font-size: 0.9em;
            margin-top: 40px;
            padding: 20px;
            background: white;
            border-radius: 10px;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        }
        .timestamp {
            color: #999;
            font-size: 0.8em;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>🚀 Jasper Load Test Reports</h1>
        <p>Comprehensive performance testing results for the Jasper Knowledge Management Server</p>
    </div>
    
    <div class="reports-container">
EOF

# Find and process all simulation directories
echo "Scanning for Gatling reports in target/gatling/..."
find target/gatling -maxdepth 1 -type d -name "*simulation-*" | sort | while read -r dir; do
    if [ -f "$dir/index.html" ]; then
        # Extract simulation name and timestamp from directory name
        dirname=$(basename "$dir")
        
        # Copy entire report directory to /report
        report_name=$(echo "$dirname" | sed 's/-[0-9]\{8\}-[0-9]\{6\}$//')
        cp -r "$dir" "$REPORT_DIR/$dirname"
        
        # Determine simulation type and description
        case "$report_name" in
            "simplejaspersimulation")
                title="Simple Jasper Simulation"
                description="Quick smoke test covering core Jasper API functionality. Tests basic CRUD operations and validates system responsiveness under light load."
                ;;
            "comprehensivejaspersimulation") 
                title="Comprehensive Jasper Simulation"
                description="Realistic knowledge management workflows simulating multiple user types: knowledge workers, administrators, and content browsers with complex operations."
                ;;
            "userjourneysimulation")
                title="User Journey Simulation" 
                description="End-to-end user journeys including research sessions, daily reviews, collaboration patterns, and content curation workflows."
                ;;
            "stresstestsimulation")
                title="Stress Test Simulation"
                description="High-volume concurrent operations testing system limits with rapid content creation, bulk updates, and edge case scenarios."
                ;;
            *)
                title="${report_name^} Simulation"
                description="Load test simulation results for $report_name"
                ;;
        esac
        
        # Add card to HTML
        cat >> "$INDEX_FILE" << EOF
        <div class="report-card">
            <div class="report-title">$title</div>
            <div class="report-description">$description</div>
            <a href="$dirname/index.html" class="report-link">View Report</a>
            <div class="timestamp">Generated: $(echo "$dirname" | grep -o '[0-9]\{8\}-[0-9]\{6\}' | sed 's/\([0-9]\{4\}\)\([0-9]\{2\}\)\([0-9]\{2\}\)-\([0-9]\{2\}\)\([0-9]\{2\}\)\([0-9]\{2\}\)/\1-\2-\3 \4:\5:\6/')</div>
        </div>
EOF
        
        echo "Added report for $title ($dirname)"
    fi
done

# Close HTML document
cat >> "$INDEX_FILE" << EOF
    </div>
    
    <div class="footer">
        <p><strong>Jasper Knowledge Management Server</strong> - Load Testing Dashboard</p>
        <p>Generated on $(date)</p>
        <p>Each simulation tests different aspects of the system with realistic user patterns and performance expectations.</p>
    </div>
</body>
</html>
EOF

echo "Generated index page at $INDEX_FILE"

# List all reports found
echo "Reports included:"
find "$REPORT_DIR" -name "index.html" -not -path "*/index.html" | sort