#!/usr/bin/env bash
# ==============================================================================
# OpenFloat M-Pesa Middleware — Production Seed Password & Key Rotator
# Enforces rotation of default development passwords, database credentials,
# and field-level encryption keys before production deployment.
# ==============================================================================

set -euo pipefail

echo "============================================================"
echo " OpenFloat Production Security Hardening & Password Rotator"
echo "============================================================"

# 1. Generate strong cryptographically secure random passwords
NEW_DB_PASS=$(openssl rand -base64 24)
NEW_REDIS_PASS=$(openssl rand -base64 24)
NEW_ENCRYPTION_KEY=$(openssl rand -base64 32)
NEW_ADMIN_PASS=$(openssl rand -base64 16)

echo ""
echo "🔑 Generated Cryptographically Secure Production Credentials:"
echo "------------------------------------------------------------"
echo " DB_PASSWORD         : $NEW_DB_PASS"
echo " REDIS_PASSWORD      : $NEW_REDIS_PASS"
echo " OPENFLOAT_ENC_KEY   : $NEW_ENCRYPTION_KEY"
echo " SEED_ADMIN_PASSWORD : $NEW_ADMIN_PASS"
echo "------------------------------------------------------------"

# 2. Output formatted production .env file
PROD_ENV_FILE=".env.production"
cat <<EOF > "$PROD_ENV_FILE"
# Generated Production Environment Overrides — $(date -u)
SPRING_DATASOURCE_USERNAME=openfloat_prod
SPRING_DATASOURCE_PASSWORD=$NEW_DB_PASS
SPRING_REDIS_PASSWORD=$NEW_REDIS_PASS
OPENFLOAT_ENCRYPTION_KEY=$NEW_ENCRYPTION_KEY
SEED_ADMIN_PASSWORD=$NEW_ADMIN_PASS
MPESA_DARAJA_BASE_URL=https://api.safaricom.co.ke
EOF

echo ""
echo "✅ Production environment secrets written to $PROD_ENV_FILE"
echo "🔒 Keep this file secure and inject into your Vault or K8s Secret store."
