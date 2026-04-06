"""
测试 SiliconFlow GLM-5 Model 是否还有额度
用法：python test_llm.py [--api-key YOUR_KEY]
"""
import argparse
import os
from openai import OpenAI


def test_gl5(api_key: str):
    client = OpenAI(
        base_url="https://api.siliconflow.cn/v1",
        api_key=api_key,
    )

    print("正在调用 SiliconFlow: Pro/zai-org/GLM-5 ...")
    print()

    try:
        resp = client.chat.completions.create(
            model="Pro/zai-org/GLM-5",
            messages=[{"role": "user", "content": "你好，请回复一句：GLM-5测试成功。"}],
            max_tokens=64,
            temperature=0.1,
        )

        content = resp.choices[0].message.content
        print(f"[OK] 调用成功!")
        print(f"   返回内容: {content}")
        print(f"   Prompt Tokens: {resp.usage.prompt_tokens if resp.usage else '?'}")
        print(f"   Completion Tokens: {resp.usage.completion_tokens if resp.usage else '?'}")
        print(f"   Total Tokens: {resp.usage.total_tokens if resp.usage else '?'}")

    except Exception as e:
        print(f"[FAIL] 调用失败!")
        print(f"   错误类型: {type(e).__name__}")
        print(f"   错误信息: {e}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-key", help="SiliconFlow API Key")
    args = parser.parse_args()

    key = args.api_key or os.environ.get("SILICONFLOW_API_KEY", "")
    if not key:
        print("❌ 未提供 API Key")
        print("用法：")
        print("  python test_llm.py --api-key <your_key>")
        print("  或设置环境变量 SILICONFLOW_API_KEY")
        exit(1)

    test_gl5(key)
