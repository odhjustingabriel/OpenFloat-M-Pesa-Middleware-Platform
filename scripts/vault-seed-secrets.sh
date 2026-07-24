#!/usr/bin/env bash
# ==============================================================================
# OpenFloat M-Pesa Middleware — HashiCorp Vault Production Secret Seeder
# Seeds production credentials into Vault KV v2 secret engine path: secret/openfloat/
# ==============================================================================

set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-root}"

export VAULT_ADDR VAULT_TOKEN

echo "==> Enabling Vault KV-V2 secret engine at path secret/..."
vault secrets enable -path=secret kv-v2 2>/dev/null || true

echo "==> Seeding Database Credentials (secret/data/openfloat/db)..."
vault kv put secret/openfloat/db \
  username="${DB_USERNAME:-openfloat_prod}" \
  password="${DB_PASSWORD:-$(openssl rand -base64 32)}" \
  url="jdbc:postgresql://postgres-prod.internal:5432/openfloat_mpesa_prod"

echo "==> Seeding Daraja API Production Credentials (secret/data/openfloat/daraja)..."
vault kv put secret/openfloat/daraja \
  consumer_key="${DARAJA_PROD_CONSUMER_KEY:-prod_daraja_consumer_key_replace_me}" \
  consumer_secret="${DARAJA_PROD_CONSUMER_SECRET:-prod_daraja_consumer_secret_replace_me}" \
  passkey="${DARAJA_PROD_PASSKEY:-prod_daraja_passkey_replace_me}" \
  shortcode="${DARAJA_PROD_SHORTCODE:-174379}" \
  initiator_password="${DARAJA_PROD_INITIATOR_PASS:-prod_initiator_password}"

echo "==> Seeding Field-Level Encryption Master Key (secret/data/openfloat/encryption)..."
vault kv put secret/openfloat/encryption \
  master_key="$(openssl rand -base64 32)"

echo "==> Seeding OAuth2 JWT Signing Keypair (secret/data/openfloat/jwt)..."
vault kv put secret/openfloat/jwt \
  rsa_private_key="$(openssl genrsa 2048 2>/dev/null | base64 -w 0)" \
  issuer="https://auth.openfloat.co.ke"

echo "✅ All OpenFloat production secrets successfully seeded into Vault!"
