<template>
  <div :class="textClass">{{ displayText }}</div>
</template>

<script>
export default {
  name: 'FormattedText',
  props: {
    text: {
      type: String,
      default: ''
    },
    code: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    displayText() {
      if (!this.text) {
        return ''
      }
      return String(this.text).replace(/\\n/g, '\n')
    },
    textClass() {
      return this.code ? 'formatted-text formatted-text--code' : 'formatted-text'
    }
  }
}
</script>

<style scoped>
.formatted-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.65;
  color: inherit;
}

.formatted-text--code {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  background: #f5f7fa;
  padding: 10px 12px;
  border-radius: 4px;
  font-size: 13px;
  border: 1px solid #ebeef5;
}
</style>
