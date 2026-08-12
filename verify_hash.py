import bcrypt
h = b'$2b$10$cyDjbRQoDWcoa5U.pnHb9eMXVTyDCPdx5nNiblR8MFPpzMykYSQMi'
result = bcrypt.checkpw(b'admin123', h)
print('admin123 match:', result)

# Also check if Spring accepts $2b - it needs $2a prefix
h2a = b'$2a$10$cyDjbRQoDWcoa5U.pnHb9eMXVTyDCPdx5nNiblR8MFPpzMykYSQMi'
result2 = bcrypt.checkpw(b'admin123', h2a)
print('admin123 match with 2a prefix:', result2)
