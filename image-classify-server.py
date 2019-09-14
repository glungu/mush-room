from fastai.vision import *
from starlette.applications import Starlette
from starlette.responses import JSONResponse, HTMLResponse
import uvicorn
import aiohttp

learner = load_learner('model')

app = Starlette(debug=True)


@app.route("/")
def init(request):
    return HTMLResponse(
    """
        <h3>This app will classify Mushrooms<h3>        
        <table>
        <tr>
        <td>
        Select image to upload: <br>
        <form action="/classify-upload" method="post" enctype="multipart/form-data">
            <input type="file" name="file"><br>
            <input type="submit" value="Upload & Classify">
        </form>        
        </td>
        </tr>

        <tr>
        <td>
        Or submit a URL:        
        <form action="/classify-url" method="get">
            <input type="url" name="url"><br>
            <input type="submit" value="Fetch URL & Classify">
        </form>
        </td>
        </tr>
        </table>
    """
    )


@app.route("/classify-upload", methods=["POST"])
async def classify_upload(request):
    data = await request.form()
    bytes = await (data["file"].read())
    print('Received classify-upload request. Bytes:', len(bytes))
    return predict_image(bytes)


@app.route("/classify-url", methods=["GET"])
async def classify_url(request):
    bytes = await get_bytes(request.query_params["url"])
    return predict_image(bytes)


async def get_bytes(url):
    async with aiohttp.ClientSession() as session:
        async with session.get(url) as response:
            return await response.read()


def predict_image(bytes):
    img = open_image(BytesIO(bytes))
    _,_,losses = learner.predict(img)
    response = {
        "predictions": sorted(
            zip(learner.data.classes, map(float, losses)),
            key=lambda p: p[1],
            reverse=True
        )
    }
    print(response)
    return JSONResponse(response)


if __name__ == '__main__':
    uvicorn.run(app, host='0.0.0.0', port=8000)