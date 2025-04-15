# Tabela de Casos e Comportamento Esperado

```md
# Cenários de Validação da `businessKey`

| businessKey         | retryCount | Possui número? | Número é par? | Resultado esperado           |
|---------------------|------------|----------------|---------------|------------------------------|
| my-business-key-4   | 0          | ✅ Sim         | ✅ Sim        | ✅ Válido → APPROVED         |
| my-business-key-5   | 0          | ✅ Sim         | ❌ Não        | ✅ Válido → REJECTED         |
| my-business-key     | 0          | ❌ Não         | —             | ❌ Inválido → Retry          |
| my-business-key     | 1          | ❌ Não         | —             | ❌ Inválido → Retry          |
| my-business-key     | 2          | ❌ Não         | —             | ✅ Lambda aplica `-01`       |
```

---

# Lógica de Processamento da `businessKey`

O processamento da `businessKey` segue as seguintes regras:

1. **Formato esperado:** a chave deve seguir o padrão `my-business-key` ou `my-business-key-{número}`.
2. Se a chave possui um **número ao final**:
    - Se o número for **par**, a mensagem será marcada como `APPROVED`.
    - Se o número for **ímpar**, será `REJECTED`, independentemente do `retryCount`.
3. Se a chave **não possui número**:
    - A mensagem será marcada como **inválida** e forçará um retry.
    - Após **2 tentativas** (`retryCount >= 2`), a Lambda ajusta automaticamente o `businessKey`, adicionando o sufixo `-01` (ex: `my-business-key-01`), e continua o fluxo normalmente com base nesse novo número.

Essa abordagem garante:
- Tratamento correto para mensagens incompletas.
- Resiliência com tentativa automática de correção após retries.
- Separação clara entre validação técnica (Camel) e correção de dados (Lambda).

---

Se quiser, posso exportar isso como um arquivo `README.md` completo ou gerar também um fluxograma `.drawio` para visual do fluxo. Deseja?